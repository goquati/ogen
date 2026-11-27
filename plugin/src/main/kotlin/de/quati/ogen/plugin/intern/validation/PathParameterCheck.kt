package de.quati.ogen.plugin.intern.validation

import de.quati.ogen.plugin.intern.oas.model.Parameter
import de.quati.ogen.plugin.intern.oas.model.RefOr
import de.quati.ogen.plugin.intern.parsing.operations

/**
 * Path parameters whose name has no `{name}` in the path template, reported where they are declared: at the path
 * for those of the Path Item, at the operation for its own.
 */
internal fun pathParameterErrors(spec: ValidatedSpec): List<String> {
    // A reference that does not resolve is reported by the parser.
    fun RefOr<Parameter>.resolve(): Parameter? = when (this) {
        is RefOr.Value -> value
        is RefOr.Ref -> ref.componentName("parameters")?.let { spec.api.components?.parameters?.get(it) }
            ?.let { (it as? RefOr.Value)?.value }
    }

    fun List<RefOr<Parameter>>?.errors(path: String, location: String): List<String> {
        val templateNames = pathVariable.findAll(path).map { it.groupValues[1] }.toSet()
        return orEmpty()
            .mapNotNull { it.resolve() }
            .filter { it.location == Parameter.Location.PATH && it.name !in templateNames }
            .map { "path parameter '${it.name}' in '$location' has no '{${it.name}}' in the path" }
    }

    // Only `paths` have a path template; webhooks and callbacks do not.
    return spec.api.paths?.pathItems.orEmpty().flatMap { (path, pathItem) ->
        pathItem.parameters.errors(path, path) +
            pathItem.operations().flatMap { (method, operation) -> operation.parameters.errors(path, "$path:${method.name}") }
    }
}
