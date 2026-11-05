package de.quati.ogen.plugin.intern.tasks

import de.quati.ogen.plugin.intern.model.config.SpecConfig
import de.quati.ogen.plugin.intern.model.config.SpecConfigs
import de.quati.ogen.plugin.intern.validation.contradictoryConstraintErrors
import de.quati.ogen.plugin.intern.validation.enumValueErrors
import de.quati.ogen.plugin.intern.validation.namingConventionErrors
import de.quati.ogen.plugin.intern.validation.nullableWithoutTypeWarnings
import de.quati.ogen.plugin.intern.validation.operations
import de.quati.ogen.plugin.intern.validation.pathParameterErrors
import de.quati.ogen.plugin.intern.validation.patternWarnings
import de.quati.ogen.plugin.intern.validation.successResponseWarnings
import de.quati.ogen.plugin.intern.validation.trailingSlashWarnings
import de.quati.ogen.plugin.intern.validation.undeclaredTagWarnings
import de.quati.ogen.plugin.intern.validation.undefinedRequiredPropertyErrors
import de.quati.ogen.plugin.intern.validation.undefinedSecuritySchemeErrors
import de.quati.ogen.plugin.intern.validation.walkSchemas
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema
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
            // A project can validate several specs; every header and exception names the one it is about.
            val specName = "spec '${config.inputConfig}'"
            logger.info("Validating $specName")

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
                logger.warn("\nThe $specName has issues or recommendations.\nIssues:\n")
                warnings.forEach { logger.warn(it) }
            }

            if (spec == null || messages.isNotEmpty() || validationResult.errors.isNotEmpty()) {
                logger.error("\nThe $specName is invalid.\nIssues:\n")

                messages.forEach { logger.error(formatIssue(it)) }

                validationResult.errors.forEach {
                    logger.error(it.format())
                }

                throw GradleException("Validation of the $specName failed.")
            }

            val errors = namingConventionErrors(spec, validatorConfig) + errorChecks.flatMap { it(spec) }
            if (errors.isNotEmpty()) {
                logger.error("\nThe $specName is invalid.\nIssues:\n")
                errors.forEach { logger.error(formatIssue(it)) }
                throw GradleException("Found ${errors.size} errors in the $specName.")
            }

            if (validatorConfig.failOnWarnings && warnings.isNotEmpty()) {
                logger.error("\nWarnings found in the $specName and 'failOnWarnings' is enabled.\nFailing validation.\n")
                throw GradleException("Validation of the $specName failed due to warnings (failOnWarnings = true).")
            }

            logger.debug("No error validations from swagger-parser or internal validations.")
            logger.info("The $specName is valid.\n")
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
            ::nullableWithoutTypeWarnings,
            ::patternWarnings,
        )

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