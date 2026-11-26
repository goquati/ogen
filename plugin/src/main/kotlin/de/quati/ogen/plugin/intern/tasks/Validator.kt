package de.quati.ogen.plugin.intern.tasks

import de.quati.ogen.plugin.intern.model.config.SpecConfig
import de.quati.ogen.plugin.intern.model.config.SpecConfigs
import de.quati.ogen.plugin.intern.validation.ValidatedSpec
import de.quati.ogen.plugin.intern.validation.ambiguousPathWarnings
import de.quati.ogen.plugin.intern.validation.brokenPathTemplateErrors
import de.quati.ogen.plugin.intern.validation.compositionTypeErrors
import de.quati.ogen.plugin.intern.validation.contradictoryConstraintErrors
import de.quati.ogen.plugin.intern.validation.defaultOutsideEnumErrors
import de.quati.ogen.plugin.intern.validation.enumValueErrors
import de.quati.ogen.plugin.intern.validation.exclusiveFlagWithoutBoundWarnings
import de.quati.ogen.plugin.intern.validation.getWithBodyWarnings
import de.quati.ogen.plugin.intern.validation.headerUnderscoreWarnings
import de.quati.ogen.plugin.intern.validation.ignoredNextToRefWarnings
import de.quati.ogen.plugin.intern.validation.nullableIn31Warnings
import de.quati.ogen.plugin.intern.validation.nullableWithoutTypeWarnings
import de.quati.ogen.plugin.intern.validation.identicalPathErrors
import de.quati.ogen.plugin.intern.validation.invalidTypeErrors
import de.quati.ogen.plugin.intern.validation.namingConventionErrors
import de.quati.ogen.plugin.intern.validation.oneOfWithPropertiesWarnings
import de.quati.ogen.plugin.intern.validation.pathParameterErrors
import de.quati.ogen.plugin.intern.validation.patternWarnings
import de.quati.ogen.plugin.intern.validation.queryOrFragmentInPathErrors
import de.quati.ogen.plugin.intern.validation.redundantBoundWarnings
import de.quati.ogen.plugin.intern.validation.successResponseWarnings
import de.quati.ogen.plugin.intern.validation.trailingSlashWarnings
import de.quati.ogen.plugin.intern.validation.typeKeywordWarnings
import de.quati.ogen.plugin.intern.validation.undeclaredTagWarnings
import de.quati.ogen.plugin.intern.validation.undefinedRequiredPropertyErrors
import de.quati.ogen.plugin.intern.validation.undefinedSecuritySchemeErrors
import de.quati.ogen.plugin.intern.validation.unsupportedOperationWarnings
import de.quati.ogen.plugin.intern.validation.unusedSchemaWarnings
import org.gradle.api.GradleException
import org.slf4j.Logger

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
        /**
         * Validates a spec in two steps. The parser's errors (a spec it cannot read, unknown fields, wrong types,
         * unresolvable references) fail validation at once; its warnings come with the others. Only a spec the
         * parser read is checked by ogen's own checks.
         */
        fun validate(
            config: SpecConfig,
            logger: Logger,
        ) {
            val validatorConfig = config.validatorConfig ?: return
            // A project can validate several specs; every header and exception names the one it is about.
            val specName = "spec '${config.inputConfig}'"
            logger.info("Validating $specName")

            val result = config.oasParseResult
            val spec = result.openApi?.let { ValidatedSpec(it, result.sourceVersion!!, result.bundle) }
            val recommendations = if (validatorConfig.recommendations) recommendationChecks else emptyList()
            val ogenWarnings = spec?.let { s -> (warningChecks + recommendations).flatMap { it(s) } }.orEmpty()
            val warnings = (result.warnings.map { it.toString() } + ogenWarnings).map(::formatIssue)

            if (warnings.isNotEmpty()) {
                logger.warn("\nThe $specName has issues or recommendations.\nIssues:\n")
                warnings.forEach { logger.warn(it) }
            }

            if (spec == null || result.errors.isNotEmpty()) {
                logger.error("\nThe $specName is invalid.\nIssues:\n")
                result.errors.forEach { logger.error(formatIssue(it.toString())) }
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

            logger.debug("No errors from the parser or the checks.")
            logger.info("The $specName is valid.\n")
        }

        // ogen's own errors; they run once the parser found no errors.
        private val errorChecks: List<(ValidatedSpec) -> List<String>> = listOf(
            ::brokenPathTemplateErrors,
            ::queryOrFragmentInPathErrors,
            ::pathParameterErrors,
            ::identicalPathErrors,
            ::undefinedSecuritySchemeErrors,
            ::enumValueErrors,
            ::defaultOutsideEnumErrors,
            ::invalidTypeErrors,
            ::contradictoryConstraintErrors,
            ::undefinedRequiredPropertyErrors,
            ::compositionTypeErrors,
        )

        // ogen's own warnings; they run on any spec the parser could read.
        private val warningChecks: List<(ValidatedSpec) -> List<String>> = listOf(
            ::trailingSlashWarnings,
            ::successResponseWarnings,
            ::undeclaredTagWarnings,
            ::patternWarnings,
            ::ambiguousPathWarnings,
            ::redundantBoundWarnings,
            ::typeKeywordWarnings,
            ::unsupportedOperationWarnings,
            ::nullableWithoutTypeWarnings,
            ::exclusiveFlagWithoutBoundWarnings,
            ::ignoredNextToRefWarnings,
            ::nullableIn31Warnings,
            ::unusedSchemaWarnings,
        )

        // Recommendations, with `recommendations` enabled: what may be intended, but often is a mistake.
        private val recommendationChecks: List<(ValidatedSpec) -> List<String>> = listOf(
            ::oneOfWithPropertiesWarnings,
            ::getWithBodyWarnings,
            ::headerUnderscoreWarnings,
        )

        private fun formatIssue(issue: String) = "\t$issue\n"
    }
}
