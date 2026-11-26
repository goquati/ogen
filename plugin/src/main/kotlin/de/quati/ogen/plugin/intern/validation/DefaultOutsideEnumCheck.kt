package de.quati.ogen.plugin.intern.validation

/** A `default` that its schema's `enum` doesn't list; values compare as JSON (`1` equals `1.0`, not `"1"`). */
internal fun defaultOutsideEnumErrors(spec: ValidatedSpec): List<String> {
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
