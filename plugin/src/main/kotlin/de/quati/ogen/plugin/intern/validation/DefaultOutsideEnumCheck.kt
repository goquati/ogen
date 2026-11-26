package de.quati.ogen.plugin.intern.validation

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A `default` that its schema's `enum` doesn't list; values compare as JSON (`1` equals `1.0`, not `"1"`).
 * In 3.0 a default that does not fit the schema's type counts as none, as swagger-parser turned it into null.
 */
internal fun defaultOutsideEnumErrors(spec: ValidatedSpec): List<String> {
    val errors = mutableListOf<String>()
    spec.walkSchemas { node ->
        val values = node.schema.enum?.takeIf { it.isNotEmpty() } ?: return@walkSchemas
        val default = node.schema.default ?: return@walkSchemas
        if (spec.is30 && !default.fits(node.schema.declaredTypes - "null")) return@walkSchemas
        if (default.enumKey() !in values.map { it.enumKey() })
            errors.add(
                "default ${default.render()} in '${node.location}' is not one of its enum values " +
                    values.joinToString { it.render() }
            )
    }
    return errors
}

private fun JsonElement.fits(types: Set<String>): Boolean {
    val type = types.singleOrNull() ?: return true
    return when (type) {
        "integer" -> jsonType == "number" && (this as JsonPrimitive).isWholeNumber()
        "number", "string", "boolean" -> jsonType == type
        "array" -> this is JsonArray
        "object" -> this is JsonObject
        else -> true
    }
}
