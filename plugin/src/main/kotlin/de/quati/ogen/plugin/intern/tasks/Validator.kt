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
import org.gradle.api.logging.Logging
import org.gradle.internal.logging.text.StyledTextOutput
import org.openapitools.codegen.validations.oas.OpenApiEvaluator
import org.openapitools.codegen.validations.oas.RuleConfiguration

internal class Validator(
    private val out: StyledTextOutput,
) {

    fun validate(
        configs: SpecConfigs,
    ) {
        configs.specs.forEach { config ->
            validate(config = config, out = out)
        }
    }


    companion object {
        private val logger = Logging.getLogger(Validator::class.java)

        fun validate(
            config: SpecConfig,
            out: StyledTextOutput,
        ) {
            val validatorConfig = config.validatorConfig ?: return
            out.withStyle(StyledTextOutput.Style.Info)
            out.println("Validating spec ${config.apiFile}")

            val parseResult = config.parseResult
            val messages = parseResult.messages.toSet()
            val evaluator = OpenApiEvaluator(RuleConfiguration().apply {
                isEnableRecommendations = validatorConfig.recommendations
            })

            val spec = parseResult.openAPI
            val validationResult = evaluator.validate(spec)

            if (validationResult.warnings.isNotEmpty()) {
                out.withStyle(StyledTextOutput.Style.Info)
                out.println("\nSpec has issues or recommendations.\nIssues:\n")

                validationResult.warnings.forEach {
                    out.withStyle(StyledTextOutput.Style.Info)
                    out.println("\t${it.message}\n")
                    logger.debug("WARNING: ${it.message}|${it.details}")
                }
            }

            if (messages.isNotEmpty() || validationResult.errors.isNotEmpty()) {
                out.withStyle(StyledTextOutput.Style.Error)
                out.println("\nSpec is invalid.\nIssues:\n")

                messages.forEach {
                    out.withStyle(StyledTextOutput.Style.Error)
                    out.println("\t$it\n")
                    logger.debug("ERROR: $it")
                }

                validationResult.errors.forEach {
                    out.withStyle(StyledTextOutput.Style.Error)
                    out.println("\t${it.message}\n")
                    logger.debug("ERROR: ${it.message}|${it.details}")
                }

                throw GradleException("Validation failed.")
            }

            if (validatorConfig.failOnWarnings && validationResult.warnings.isNotEmpty()) {
                out.withStyle(StyledTextOutput.Style.Error)
                out.println("\nWarnings found in the spec and 'treatWarningsAsErrors' is enabled.\nFailing validation.\n")
                throw GradleException("Validation failed due to warnings (treatWarningsAsErrors = true).")
            }

            val namingConventionErrors = getNamingConventionErrors(spec, validatorConfig)
            if (namingConventionErrors.isNotEmpty()) {
                namingConventionErrors.forEach {
                    out.withStyle(StyledTextOutput.Style.Error)
                    out.println("\t${it}")
                }
                throw GradleException("Found ${namingConventionErrors.size} naming convention errors.")
            }

            out.withStyle(StyledTextOutput.Style.Success)
            logger.debug("No error validations from swagger-parser or internal validations.")
            out.println("Spec is valid.\n")
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
    }
}