package de.quati.ogen.plugin.intern.validation

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.SpecVersion

private val types30 = setOf("array", "boolean", "integer", "number", "object", "string")
private val types31 = types30 + "null"

/** A `type` that isn't a JSON Schema type; the generator falls back to an untyped JSON value for it. */
internal fun invalidTypeErrors(spec: OpenAPI): List<String> {
    val isV31 = spec.specVersion == SpecVersion.V31
    val valid = if (isV31) types31 else types30
    val errors = mutableListOf<String>()
    spec.walkSchemas { node ->
        node.schema.declaredTypes(isV31).filter { it !in valid }.forEach {
            errors.add("type '$it' in '${node.location}' is not one of ${valid.sorted().joinToString()}")
        }
    }
    return errors
}
