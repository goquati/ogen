package de.quati.ogen.plugin.intern.validation

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.math.BigDecimal

/** The JSON Schema types that take numeric keywords; an integer is a number. */
internal val numberTypes = setOf("integer", "number")

/** A set of types as messages show it: `integer or null`. */
internal fun Set<String>.typeList() = sorted().joinToString(" or ")

/** Equal for values that are equal in JSON: `1` and `1.0`, but not `1` and `"1"`. */
internal fun JsonElement.enumKey(): Any = when {
    this is JsonPrimitive && this !is JsonNull && !isString && booleanOrNull == null ->
        content.toBigDecimalOrNull()?.stripTrailingZeros() ?: content
    else -> this
}

/** The JSON type of a value: `string`, `number`, `boolean`, `null`, or null for an object or array. */
internal val JsonElement.jsonType: String?
    get() = when {
        this is JsonNull -> "null"
        this !is JsonPrimitive -> null
        isString -> "string"
        booleanOrNull != null -> "boolean"
        else -> "number"
    }

/** Whether a number has no fractional part, so an integer schema allows it. */
internal fun JsonPrimitive.isWholeNumber() = content.toBigDecimalOrNull()?.stripTrailingZeros()?.let { it.scale() <= 0 } == true

/** A schema value as messages show it: strings quoted. */
internal fun JsonElement.render() = if (this is JsonPrimitive && isString) "'$content'" else toString()

private fun String.toBigDecimalOrNull() = runCatching { BigDecimal(this) }.getOrNull()
