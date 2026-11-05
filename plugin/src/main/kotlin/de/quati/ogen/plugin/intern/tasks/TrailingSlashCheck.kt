package de.quati.ogen.plugin.intern.tasks

import io.swagger.v3.oas.models.OpenAPI
import org.openapitools.codegen.validation.ValidationResult
import org.openapitools.codegen.validation.ValidationRule
import org.openapitools.codegen.validation.Validated

private val trailingSlashRule = ValidationRule.warn(
    "Path keys should not end with a slash",
    "Path has a trailing slash",
) { _: String -> ValidationRule.Fail.empty() }

internal fun ValidationResult.addTrailingSlashWarnings(spec: OpenAPI?) {
    spec?.paths.orEmpty().keys
        // '/' is how the root path is written, not a trailing slash.
        .filter { it != "/" && it.endsWith('/') }
        .forEach { path ->
            addResult(Validated.invalid(trailingSlashRule, "Path '$path' has a trailing slash."))
        }
}
