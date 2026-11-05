package de.quati.ogen.plugin.intern.tasks

import io.swagger.v3.oas.models.OpenAPI
import org.openapitools.codegen.validation.ValidationResult
import org.openapitools.codegen.validation.ValidationRule
import org.openapitools.codegen.validation.Validated

// Only gives the warnings their severity; never evaluated.
private val undeclaredTagRule = ValidationRule.warn<OpenAPI>(
    "Operation tags should be declared in the top-level tags",
    "Operation tag is not declared in the top-level tags",
) { ValidationRule.Pass.empty() }

/**
 * Warns about each operation tag missing from the top-level `tags`. A spec that declares no top-level tags
 * opts out of declaring them, so it gets no warnings.
 */
internal fun ValidationResult.addUndeclaredTagWarnings(spec: OpenAPI?) {
    val declaredTags = spec?.tags?.mapNotNull { it.name }?.toSet()
    if (declaredTags.isNullOrEmpty()) return
    spec.paths.orEmpty().forEach { (path, pathItem) ->
        pathItem.readOperationsMap().forEach { (method, operation) ->
            operation.tags.orEmpty().distinct().filter { it !in declaredTags }.forEach { tag ->
                addResult(
                    Validated.invalid(
                        undeclaredTagRule,
                        "tag '$tag' in '$path:${method.name}' is not declared in the top-level tags",
                    )
                )
            }
        }
    }
}
