package de.quati.ogen.plugin.intern.tasks

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import org.openapitools.codegen.validation.ValidationResult
import org.openapitools.codegen.validation.ValidationRule
import org.openapitools.codegen.validation.Validated

// 3xx counts as success, as in Spectral's operation-success-response: a redirect-only endpoint is legitimate.
// Case-insensitive like HttpCode.parse, so the validator accepts the "2xx" keys the generator accepts.
private val successResponseCode = Regex("[23](\\d\\d|XX)", RegexOption.IGNORE_CASE)

private val successResponseRule = ValidationRule.warn<Operation>(
    "Operations should declare a success (2xx) or redirect (3xx) response.",
    "Operation has no 2xx or 3xx response.",
) { operation ->
    if (operation.responses.orEmpty().keys.any(successResponseCode::matches))
        ValidationRule.Pass.empty()
    else
        ValidationRule.Fail.empty()
}

internal fun ValidationResult.addSuccessResponseWarnings(spec: OpenAPI) {
    spec.paths.orEmpty().forEach { (path, pathItem) ->
        pathItem.readOperationsMap().forEach { (method, operation) ->
            if (successResponseRule.evaluate(operation).failed())
                addResult(Validated.invalid(successResponseRule, "operation '$path:${method.name}' has no 2xx or 3xx response"))
        }
    }
}
