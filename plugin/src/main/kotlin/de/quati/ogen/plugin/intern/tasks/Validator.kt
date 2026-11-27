package de.quati.ogen.plugin.intern.tasks

import de.quati.ogen.plugin.Severity
import de.quati.ogen.plugin.intern.model.config.SpecConfig
import de.quati.ogen.plugin.intern.model.config.SpecConfigs
import de.quati.ogen.plugin.intern.validation.Rule
import de.quati.ogen.plugin.intern.validation.ValidatedSpec
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
         * Validates a spec. The parser's errors (a spec it cannot read, unknown fields, wrong types, unresolvable
         * references) fail validation; its warnings come with the others. Only a spec the parser read is checked by
         * ogen's rules, and the rules that are errors by default only one it found no errors in. Each rule reports
         * with the severity the validator config gives it.
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
            val parserFailed = spec == null || result.errors.isNotEmpty()
            val issues = spec?.let { s ->
                Rule.entries
                    .map { it to validatorConfig.severity(it) }
                    .filter { (rule, severity) -> severity != Severity.Off && !(parserFailed && rule.needsValidSpec) }
                    .flatMap { (rule, severity) -> rule.check(s, validatorConfig).map { severity to it } }
            }.orEmpty()
            val warnings = (result.warnings.map { it.toString() } + issues.of(Severity.Warning)).map(::formatIssue)
            val errors = result.errors.map { it.toString() } + issues.of(Severity.Error)

            if (warnings.isNotEmpty()) {
                logger.warn("\nThe $specName has issues or recommendations.\nIssues:\n")
                warnings.forEach { logger.warn(it) }
            }

            if (parserFailed || errors.isNotEmpty()) {
                logger.error("\nThe $specName is invalid.\nIssues:\n")
                errors.forEach { logger.error(formatIssue(it)) }
                throw GradleException(
                    if (parserFailed) "Validation of the $specName failed."
                    else "Found ${errors.size} errors in the $specName."
                )
            }

            if (validatorConfig.failOnWarnings && warnings.isNotEmpty()) {
                logger.error("\nWarnings found in the $specName and 'failOnWarnings' is enabled.\nFailing validation.\n")
                throw GradleException("Validation of the $specName failed due to warnings (failOnWarnings = true).")
            }

            logger.debug("No errors from the parser or the checks.")
            logger.info("The $specName is valid.\n")
        }

        private fun List<Pair<Severity, String>>.of(severity: Severity) = filter { it.first == severity }.map { it.second }

        private fun formatIssue(issue: String) = "\t$issue\n"
    }
}
