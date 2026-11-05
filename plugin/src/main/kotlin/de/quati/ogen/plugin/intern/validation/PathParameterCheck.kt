package de.quati.ogen.plugin.intern.validation

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.parameters.Parameter

private val pathTemplateVariable = Regex("""\{([^{}]+)}""")

/**
 * Path parameters whose name has no `{name}` in the path template; swagger-parser reports the opposite direction.
 * In OpenAPI 3.0 the parser moves path-level parameters into each operation, so they are reported per operation.
 */
internal fun pathParameterErrors(spec: OpenAPI): List<String> {
    // A `$ref` that does not resolve is left to swagger-parser, which reports it.
    fun Parameter.resolve(): Parameter? =
        `$ref`?.let { ref -> ref.componentName("parameters")?.let { spec.components?.parameters?.get(it) } } ?: this

    fun List<Parameter>?.errors(path: String, location: String): List<String> {
        val templateNames = pathTemplateVariable.findAll(path).map { it.groupValues[1] }.toSet()
        return orEmpty()
            .mapNotNull { it.resolve() }
            .filter { it.`in` == "path" && it.name !in templateNames }
            .map { "path parameter '${it.name}' in '$location' has no '{${it.name}}' in the path" }
    }

    // Only `paths` have a path template; webhooks and callbacks do not.
    return spec.paths.orEmpty().flatMap { (path, pathItem) ->
        pathItem.parameters.errors(path, path) +
            pathItem.readOperationsMap().flatMap { (method, operation) -> operation.parameters.errors(path, "$path:${method.name}") }
    }
}
