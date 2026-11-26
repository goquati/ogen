package de.quati.ogen.plugin.intern.validation

// After migration a 3.0 schema may carry `null`, which stands for its `nullable`; so `null` is valid for every version.
private val validTypes = setOf("array", "boolean", "integer", "null", "number", "object", "string")

/** A `type` that isn't a JSON Schema type; the generator falls back to an untyped JSON value for it. */
internal fun invalidTypeErrors(spec: ValidatedSpec): List<String> {
    val valid = if (spec.is30) validTypes - "null" else validTypes
    val errors = mutableListOf<String>()
    spec.walkSchemas { node ->
        node.schema.declaredTypes.filter { it !in validTypes }.forEach {
            errors.add("type '$it' in '${node.location}' is not one of ${valid.sorted().joinToString()}")
        }
    }
    return errors
}
