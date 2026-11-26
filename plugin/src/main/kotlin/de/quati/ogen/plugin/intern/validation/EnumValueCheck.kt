package de.quati.ogen.plugin.intern.validation

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Enum values that do not match their schema's type, `null` in a schema that is not nullable, and values listed
 * twice. Values are compared as written in the spec, read as YAML 1.2: `1` equals `1.0`, but not `"1"`.
 */
internal fun enumValueErrors(spec: ValidatedSpec): List<String> {
    val errors = mutableListOf<String>()
    spec.walkSchemas { node ->
        val schema = node.schema
        val values = schema.enum?.takeIf { it.isNotEmpty() } ?: return@walkSchemas
        val where = "'${node.location}'"
        val declared = schema.declaredTypes
        val nullAllowed = "null" in declared
        val valueTypes = declared - "null"
        val reported = mutableSetOf<Any>()
        values.forEach { value ->
            val key = value.enumKey()
            when {
                key in reported -> Unit
                value is JsonNull && !nullAllowed ->
                    errors.add("enum value null in $where is not allowed: the schema is not nullable")

                value !is JsonNull && valueTypes.isNotEmpty() && !value.matchesAny(valueTypes) -> errors.add(
                    "enum value ${value.render()} in $where does not match the schema's type ${valueTypes.joinToString(" or ")}"
                )

                values.count { it.enumKey() == key } > 1 ->
                    errors.add("enum value ${value.render()} in $where is listed more than once")

                else -> return@forEach
            }
            reported.add(key)
        }
    }
    return errors
}

private fun kotlinx.serialization.json.JsonElement.matchesAny(types: Set<String>) = when (jsonType) {
    "string" -> "string" in types
    "boolean" -> "boolean" in types
    "number" -> "number" in types || ("integer" in types && (this as JsonPrimitive).isWholeNumber())
    else -> true // An object or array value is not checked.
}
