package de.quati.ogen.plugin.intern.validation

import de.quati.ogen.plugin.NameConvention
import de.quati.ogen.plugin.intern.model.config.ValidatorConfig
import io.swagger.v3.oas.models.OpenAPI

/**
 * Names that don't match the validator's naming conventions: path segments, tags and operationIds of every operation,
 * every parameter, component schema names, and the property names and string enum values of every schema.
 */
internal fun namingConventionErrors(spec: OpenAPI, config: ValidatorConfig): List<String> {
    val errors = mutableListOf<String>()
    fun check(kind: String, name: String, location: String, format: NameConvention) {
        if (!format.matches(name)) errors.add("$kind '$name' in '$location' does not match the $kind format $format")
    }

    spec.paths.orEmpty().keys.forEach { path ->
        path.trimStart('/').split("/")
            // The root path `/`, a trailing slash and a double slash leave an empty segment, which is no name.
            .filter { it.isNotEmpty() }
            // A segment with a variable (`{id}`, `{id}:cancel`) is not checked.
            .filter { !pathVariable.containsMatchIn(it) }
            .forEach { check("path segment", it, path, config.pathSegmentFormat) }
    }

    spec.operations().forEach { node ->
        node.operation.tags.orEmpty().forEach { check("tag", it, node.location, config.tagFormat) }
        node.operation.operationId?.let { check("operationId", it, node.location, config.operationIdFormat) }
    }

    spec.parameters().forEach { node ->
        // A `$ref` has no name of its own; the component it names is checked where it is declared.
        val name = node.parameter.name ?: return@forEach
        val placement = node.parameter.`in`
        val format = when (placement) {
            "path" -> config.parameterFormat.path
            "query" -> config.parameterFormat.query
            "header" -> config.parameterFormat.header
            "cookie" -> config.parameterFormat.cookie
            else -> error("Unknown parameter location: $placement")
        }
        check("$placement parameter", name, node.ownerLocation, format)
    }

    spec.components?.schemas.orEmpty().keys.forEach {
        check("schema name", it, "components.schemas", config.schemaNameFormat)
    }
    spec.walkSchemas { node ->
        node.schema.properties.orEmpty().keys
            .forEach { check("property", it, node.location, config.propertyNameFormat) }
        // A naming convention can only apply to a string value.
        node.schema.enum.orEmpty().filterIsInstance<String>()
            .forEach { check("enum value", it, node.location, config.stringEnumFormat) }
    }
    return errors
}
