package de.quati.ogen.plugin.intern.validation

import io.swagger.v3.oas.models.OpenAPI

/**
 * Operation tags missing from the top-level `tags`. A spec that declares no top-level tags opts out of declaring
 * them, so it gets no warnings.
 */
internal fun undeclaredTagWarnings(spec: OpenAPI): List<String> {
    val declared = spec.tags?.mapNotNull { it.name }?.toSet()
    if (declared.isNullOrEmpty()) return emptyList()
    return spec.operations().flatMap { node ->
        node.operation.tags.orEmpty().distinct().filter { it !in declared }
            .map { "tag '$it' in '${node.location}' is not declared in the top-level tags" }
    }
}
