package de.quati.ogen.plugin.intern.tasks

import de.quati.ogen.plugin.intern.model.config.SpecConfig
import de.quati.ogen.plugin.intern.model.config.SpecConfigs
import de.quati.ogen.plugin.intern.model.config.ValidatorConfig
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.Parameter
import org.gradle.api.GradleException
import org.slf4j.Logger
import org.openapitools.codegen.validation.Invalid
import org.openapitools.codegen.validations.oas.OpenApiEvaluator
import org.openapitools.codegen.validations.oas.RuleConfiguration
import java.util.Collections
import java.util.IdentityHashMap

internal class Validator(
    private val logger: Logger,
) {

    fun validate(
        configs: SpecConfigs,
    ) {
        configs.specs.forEach { config ->
            validate(config = config, logger = logger)
        }
    }


    companion object {
        fun validate(
            config: SpecConfig,
            logger: Logger,
        ) {
            val validatorConfig = config.validatorConfig ?: return
            logger.info("Validating spec ${config.inputConfig}")

            val parseResult = config.parseResult
            val messages = parseResult.messages.toSet()
            val evaluator = OpenApiEvaluator(RuleConfiguration().apply {
                isEnableRecommendations = validatorConfig.recommendations
            })

            val spec = parseResult.openAPI
            val validationResult = spec.withLocationNames { evaluator.validate(spec) }

            if (validationResult.warnings.isNotEmpty()) {
                logger.warn("\nSpec has issues or recommendations.\nIssues:\n")

                validationResult.warnings.forEach {
                    logger.warn(it.format())
                }
            }

            if (messages.isNotEmpty() || validationResult.errors.isNotEmpty()) {
                logger.error("\nSpec is invalid.\nIssues:\n")

                messages.forEach {
                    logger.error("\t$it\n")
                    logger.debug("ERROR: $it")
                }

                validationResult.errors.forEach {
                    logger.error(it.format())
                }

                throw GradleException("Validation failed.")
            }

            if (validatorConfig.failOnWarnings && validationResult.warnings.isNotEmpty()) {
                logger.error("\nWarnings found in the spec and 'failOnWarnings' is enabled.\nFailing validation.\n")
                throw GradleException("Validation failed due to warnings (failOnWarnings = true).")
            }

            val namingConventionErrors = getNamingConventionErrors(spec, validatorConfig)
            if (namingConventionErrors.isNotEmpty()) {
                namingConventionErrors.forEach {
                    logger.error("\t${it}")
                }
                throw GradleException("Found ${namingConventionErrors.size} naming convention errors.")
            }

            logger.debug("No error validations from swagger-parser or internal validations.")
            logger.info("Spec is valid.\n")
        }

        fun getNamingConventionErrors(
            spec: OpenAPI,
            config: ValidatorConfig,
        ): List<String> {
            val errors = mutableListOf<String>()

            fun Schema<*>.check(path: String): Unit = when (this) {
                is ObjectSchema -> properties?.forEach { (name, prop) ->
                    if (!config.propertyNameFormat.matches(name))
                        errors.add("property '$name' in $path matches not parameter format ${config.propertyNameFormat}")
                    prop.check(path = "$path.$name")
                }

                is ArraySchema -> items?.check(path = "$path[]")
                is StringSchema -> {
                    enum?.filterNotNull()?.forEach {
                        if (!config.stringEnumFormat.matches(it))
                            errors.add("enum value '$it' in $path matches not enum format ${config.stringEnumFormat}")
                    }
                }

                else -> Unit
            }.let { }

            fun Parameter.check(path: String) {
                val name = name
                schema?.check(path = "$path.$name")
                if (name != null) {
                    val format = when (this.`in`) {
                        "path" -> config.parameterFormat.path
                        "query" -> config.parameterFormat.query
                        "cookie" -> config.parameterFormat.cookie
                        "header" -> config.parameterFormat.header
                        else -> error("Unknown parameter location: ${this.`in`}")
                    }
                    if (!format.matches(name))
                        errors.add("parameter '$name' $path matches not parameter format $format")
                }
            }

            fun Operation.check(path: String) {
                tags?.filter { !config.tagFormat.matches(it) }
                    ?.forEach { errors.add("tag '$it' in '$path' matches not tag format ${config.tagFormat}") }
                operationId?.takeIf { !config.operationIdFormat.matches(it) }
                    ?.let { errors.add("operationId '$it' in '$path' matches not operationId format ${config.operationIdFormat}") }
                parameters?.forEach { it.check(path = path) }
            }

            spec.paths.forEach { (path, pathItem) ->
                path.trimStart('/').split("/")
                    .filter { !it.startsWith("{") && !it.endsWith("}") }
                    .filter { !config.pathSegmentFormat.matches(it) }
                    .forEach {
                        errors.add("path segment '$it' in '$path' matches path segment format ${config.pathSegmentFormat}")
                    }
                pathItem.parameters?.forEach { it.schema?.check(path) }
                pathItem.readOperationsMap().forEach { (method, operation) ->
                    operation.check("${path}:${method.name}")
                }
            }

            spec.components.schemas.forEach { (name, schema) ->
                name.takeIf { !config.schemaNameFormat.matches(name) }
                    ?.let { errors.add("schema name '$it' matches not schemaName format ${config.schemaNameFormat}") }
                schema.check(path = name)
            }
            return errors
        }

        // The evaluator's details name a schema by its `name` and an operation by its operationId, which
        // swagger-parser leaves empty and a spec may omit; set them for the evaluation only.
        private fun <T> OpenAPI?.withLocationNames(evaluate: () -> T): T {
            val namedSchemas = mutableListOf<Schema<*>>()
            val visited = Collections.newSetFromMap(IdentityHashMap<Schema<*>, Boolean>())
            fun Schema<*>.name(path: String) {
                if (`$ref` != null || !visited.add(this)) return
                if (name == null) {
                    name = path
                    namedSchemas.add(this)
                }
                properties?.forEach { (prop, schema) -> schema.name(path = "$path.$prop") }
                items?.name(path = "$path[]")
                (additionalProperties as? Schema<*>)?.name(path = "$path{}")
                allOf?.forEachIndexed { i, schema -> schema.name(path = "$path.allOf[$i]") }
                oneOf?.forEachIndexed { i, schema -> schema.name(path = "$path.oneOf[$i]") }
                anyOf?.forEachIndexed { i, schema -> schema.name(path = "$path.anyOf[$i]") }
                not?.name(path = "$path.not")
            }
            this?.components?.schemas?.forEach { (name, schema) -> schema.name(path = name) }
            val namedOperations = this?.paths.orEmpty().flatMap { (path, pathItem) ->
                pathItem.readOperations().filter { it.operationId == null }.onEach { it.operationId = path }
            }
            try {
                return evaluate()
            } finally {
                namedSchemas.forEach { it.name = null }
                namedOperations.forEach { it.operationId = null }
            }
        }

        // The details name the schema or operation; some rules (e.g. unused schemas, duplicate tags) give none.
        private fun Invalid.format(): String =
            if (details.isNullOrBlank()) "\t$message\n" else "\t$message\n\t\t$details\n"
    }
}