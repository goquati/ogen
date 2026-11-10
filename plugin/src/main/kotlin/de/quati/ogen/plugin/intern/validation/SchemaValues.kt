package de.quati.ogen.plugin.intern.validation

import io.swagger.v3.oas.models.media.Schema
import java.math.BigDecimal
import java.util.Base64

/** The types a schema declares: OpenAPI 3.1 allows a list, 3.0 a single `type`. */
internal fun Schema<*>.declaredTypes(isV31: Boolean): Set<String> = if (isV31) types.orEmpty() else setOfNotNull(type)

/** The JSON Schema types that take numeric keywords; an integer is a number. */
internal val numberTypes = setOf("integer", "number")

/** A set of types as messages show it: `integer or null`. */
internal fun Set<String>.typeList() = sorted().joinToString(" or ")

/** Equal for values that are equal in JSON: `1` and `1.0`, but not `1` and `"1"`. */
internal fun Any?.enumKey(): Any? = when (this) {
    is Number -> BigDecimal(toString()).stripTrailingZeros()
    // swagger-parser reads `format: byte` values as arrays, which compare by identity.
    is ByteArray -> toList()
    else -> this
}

/** A schema value as messages show it: strings quoted, byte arrays as their Base64 text. */
internal fun Any?.render() = when (this) {
    is String -> "'$this'"
    is ByteArray -> "'${Base64.getEncoder().encodeToString(this)}'"
    else -> toString()
}
