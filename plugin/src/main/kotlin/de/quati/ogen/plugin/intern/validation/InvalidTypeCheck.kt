package de.quati.ogen.plugin.intern.validation

private val validTypes = setOf("array", "boolean", "integer", "null", "number", "object", "string")

/** A `type` that isn't a JSON Schema type; the generator falls back to an untyped JSON value for it. */
internal fun invalidTypeErrors(spec: ValidatedSpec): List<String> {
    val valid = if (spec.is30) validTypes - "null" else validTypes
    val errors = mutableListOf<String>()
    spec.walkSchemas { node ->
        // For 3.0 the types as written; migration's `null` for `nullable` is no type the author wrote.
        val types = if (spec.is30) node.source?.get("type")?.writtenTypes() ?: (node.schema.declaredTypes - "null") else node.schema.declaredTypes
        types.filter { it !in valid }.forEach {
            errors.add("type '$it' in '${node.location}' is not one of ${valid.sorted().joinToString()}")
        }
    }
    return errors
}
