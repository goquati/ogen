package de.quati.ogen.plugin.intern.validation

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.SpecVersion
import io.swagger.v3.oas.models.media.Schema
import java.math.BigDecimal

/**
 * Enum values that do not match their schema's type or are listed twice, as far as swagger-parser's model shows them:
 * it casts 3.0 values to the type (null if it can't), and types a 3.1 schema without `type` by its first value.
 */
internal fun enumValueErrors(spec: OpenAPI): List<String> {
    val isV31 = spec.specVersion == SpecVersion.V31
    val errors = mutableListOf<String>()
    spec.walkSchemas { node ->
        val schema = node.schema
        val values = schema.enum?.takeIf { it.isNotEmpty() } ?: return@walkSchemas
        val where = "'${node.location}'"
        val declared = when {
            isV31 -> schema.types.orEmpty()
            // In 3.0 a schema without `type` stays a plain Schema, typed only by inference.
            schema::class == Schema::class -> emptySet()
            else -> setOfNotNull(schema.type)
        }
        val nullAllowed = if (isV31) "null" in declared else schema.nullable == true
        val valueTypes = declared - "null"
        val castHint = if (!isV31 && schema.type in setOf("integer", "number"))
            " (swagger-parser also reads a value that is not a valid ${schema.type} as null)"
        else ""
        val reported = mutableSetOf<Any?>()
        values.forEach { value ->
            val key = value.enumKey()
            when {
                key in reported -> Unit
                value == null && !nullAllowed ->
                    errors.add("enum value null in $where is not allowed: the schema is not nullable$castHint")

                value != null && valueTypes.isNotEmpty() && !value.matchesAny(valueTypes) -> errors.add(
                    "enum value ${value.render()} in $where does not match the schema's type ${valueTypes.joinToString(" or ")}"
                )

                values.count { it.enumKey() == key } > 1 ->
                    errors.add("enum value ${value.render()} in $where is listed more than once" + if (value == null) castHint else "")

                else -> return@forEach
            }
            reported.add(key)
        }
    }
    return errors
}

/** Equal for values that are equal in JSON: `1` and `1.0`, but not `1` and `"1"`. */
private fun Any?.enumKey(): Any? = when (this) {
    is Number -> BigDecimal(toString()).stripTrailingZeros()
    else -> this
}

private fun Any.matchesAny(types: Set<String>) = when (this) {
    is String -> "string" in types
    is Boolean -> "boolean" in types
    is Number -> "number" in types ||
        ("integer" in types && BigDecimal(toString()).stripTrailingZeros().scale() <= 0)

    else -> true // An object or array value is not checked.
}

private fun Any?.render() = if (this is String) "'$this'" else toString()
