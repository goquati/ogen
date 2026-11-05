package de.quati.ogen.plugin.intern.tasks

import de.quati.ogen.plugin.intern.model.config.SpecConfig
import de.quati.ogen.plugin.intern.model.config.SpecConfigs
import de.quati.ogen.plugin.intern.model.config.ValidatorConfig
import de.quati.ogen.plugin.intern.validation.contradictoryConstraintErrors
import de.quati.ogen.plugin.intern.validation.enumValueErrors
import de.quati.ogen.plugin.intern.validation.operations
import de.quati.ogen.plugin.intern.validation.pathParameterErrors
import de.quati.ogen.plugin.intern.validation.successResponseWarnings
import de.quati.ogen.plugin.intern.validation.trailingSlashWarnings
import de.quati.ogen.plugin.intern.validation.undeclaredTagWarnings
import de.quati.ogen.plugin.intern.validation.undefinedRequiredPropertyErrors
import de.quati.ogen.plugin.intern.validation.undefinedSecuritySchemeErrors
import de.quati.ogen.plugin.intern.validation.walkSchemas
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
            val ogenWarnings = if (spec == null) emptyList() else warningChecks.flatMap { it(spec) }
            val warnings = validationResult.warnings.map { it.format() } + ogenWarnings.map(::formatIssue)

            if (warnings.isNotEmpty()) {
                logger.warn("\nSpec has issues or recommendations.\nIssues:\n")
                warnings.forEach { logger.warn(it) }
            }

            if (spec == null || messages.isNotEmpty() || validationResult.errors.isNotEmpty()) {
                logger.error("\nSpec is invalid.\nIssues:\n")

                messages.forEach { logger.error(formatIssue(it)) }

                validationResult.errors.forEach {
                    logger.error(it.format())
                }

                throw GradleException("Validation failed.")
            }

            val errors = getNamingConventionErrors(spec, validatorConfig) + errorChecks.flatMap { it(spec) }
            if (errors.isNotEmpty()) {
                logger.error("\nSpec is invalid.\nIssues:\n")
                errors.forEach { logger.error(formatIssue(it)) }
                throw GradleException("Found ${errors.size} errors in the spec.")
            }

            if (validatorConfig.failOnWarnings && warnings.isNotEmpty()) {
                logger.error("\nWarnings found in the spec and 'failOnWarnings' is enabled.\nFailing validation.\n")
                throw GradleException("Validation failed due to warnings (failOnWarnings = true).")
            }

            logger.debug("No error validations from swagger-parser or internal validations.")
            logger.info("Spec is valid.\n")
        }

        // ogen's own errors; they run once swagger-parser and openapi-generator found no errors.
        private val errorChecks: List<(OpenAPI) -> List<String>> = listOf(
            ::pathParameterErrors,
            ::undefinedSecuritySchemeErrors,
            ::enumValueErrors,
            ::contradictoryConstraintErrors,
            ::undefinedRequiredPropertyErrors,
        )

        // ogen's own warnings; they run on any spec swagger-parser could parse, next to openapi-generator's.
        private val warningChecks: List<(OpenAPI) -> List<String>> = listOf(
            ::trailingSlashWarnings,
            ::successResponseWarnings,
            ::undeclaredTagWarnings,
        )

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

            spec.paths?.forEach { (path, pathItem) ->
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

            spec.components?.schemas?.forEach { (name, schema) ->
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
            this?.walkSchemas { node ->
                if (node.schema.`$ref` == null && node.schema.name == null) {
                    node.schema.name = node.location
                    namedSchemas.add(node.schema)
                }
            }
            val namedOperations = this?.operations().orEmpty()
                .filter { it.operation.operationId == null }
                .onEach { it.operation.operationId = it.pathItemLocation }
                .map { it.operation }
            try {
                return evaluate()
            } finally {
                namedSchemas.forEach { it.name = null }
                namedOperations.forEach { it.operationId = null }
            }
        }

        // The details name the schema or operation; some rules (e.g. unused schemas, duplicate tags) give none.
        private fun Invalid.format(): String =
            if (details.isNullOrBlank()) formatIssue(message) else "\t$message\n\t\t$details\n"

        private fun formatIssue(issue: String) = "\t$issue\n"
    }
}