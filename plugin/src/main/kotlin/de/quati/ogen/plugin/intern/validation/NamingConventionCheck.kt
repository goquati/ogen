package de.quati.ogen.plugin.intern.validation

import de.quati.ogen.plugin.NameConvention
import de.quati.ogen.plugin.intern.model.config.ValidatorConfig
import de.quati.ogen.plugin.intern.oas.model.Parameter
import kotlinx.serialization.json.JsonPrimitive

/**
 * Names that don't match the validator's naming conventions: path segments, tags and operationIds of every operation,
 * every parameter, component schema names, and the property names and string enum values of every schema.
 */
internal fun namingConventionErrors(spec: ValidatedSpec, config: ValidatorConfig): List<String> {
    val errors = mutableListOf<String>()
    fun check(kind: String, name: String, location: String, format: NameConvention) {
        if (!format.matches(name)) errors.add("$kind '$name' in '$location' does not match the $kind format $format")
    }

    spec.api.paths?.pathItems.orEmpty().keys.forEach { path ->
        path.trimStart('/').split("/")
            // The root path `/`, a trailing slash and a double slash leave an empty segment, which is no name.
            .filter { it.isNotEmpty() }
            // A segment with a variable (`{id}`, `{id}:cancel`) is not checked.
            .filter { !pathVariable.containsMatchIn(it) }
            .forEach { check("path segment", it, path, config.pathSegmentFormat) }
    }

    spec.operations().forEach { node ->
        node.operation.tags.orEmpty().forEach { check("tag", it, node.location, config.tagFormat) }
        // A blank operationId counts as none.
        node.operation.operationId?.takeIf { it.isNotBlank() }?.let { check("operationId", it, node.location, config.operationIdFormat) }
    }

    spec.parameters().forEach { node ->
        val parameter = node.parameter
        // A parameter without name or location is reported by the parser.
        val name = parameter.name ?: return@forEach
        val (placement, format) = when (parameter.location ?: return@forEach) {
            Parameter.Location.PATH -> "path" to config.parameterFormat.path
            Parameter.Location.QUERY -> "query" to config.parameterFormat.query
            // The whole query string as one parameter (3.2) is named like a query parameter.
            Parameter.Location.QUERYSTRING -> "querystring" to config.parameterFormat.query
            Parameter.Location.HEADER -> "header" to config.parameterFormat.header
            Parameter.Location.COOKIE -> "cookie" to config.parameterFormat.cookie
        }
        check("$placement parameter", name, node.ownerLocation, format)
    }

    spec.api.components?.schemas.orEmpty().keys.forEach {
        check("schema name", it, "components.schemas", config.schemaNameFormat)
    }
    spec.walkSchemas { node ->
        node.schema.properties.orEmpty().keys
            .forEach { check("property", it, node.location, config.propertyNameFormat) }
        // A naming convention can only apply to a string value.
        node.schema.enum.orEmpty().filterIsInstance<JsonPrimitive>().filter { it.isString }
            .forEach { check("enum value", it.content, node.location, config.stringEnumFormat) }
    }
    return errors
}
