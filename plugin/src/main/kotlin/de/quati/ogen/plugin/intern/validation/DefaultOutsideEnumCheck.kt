package de.quati.ogen.plugin.intern.validation

import io.swagger.v3.oas.models.OpenAPI

/**
 * A `default` that its schema's `enum` doesn't list. In 3.0 swagger-parser turns a default that doesn't fit the
 * schema's type into null, which this can't tell from no default.
 */
internal fun defaultOutsideEnumErrors(spec: OpenAPI): List<String> {
    val errors = mutableListOf<String>()
    spec.walkSchemas { node ->
        val values = node.schema.enum?.takeIf { it.isNotEmpty() } ?: return@walkSchemas
        val default = node.schema.default ?: return@walkSchemas
        if (default.enumKey() !in values.map { it.enumKey() })
            errors.add(
                "default ${default.render()} in '${node.location}' is not one of its enum values " +
                    values.joinToString { it.render() }
            )
    }
    return errors
}
