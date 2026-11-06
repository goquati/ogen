package de.quati.ogen.plugin.intern.validation

import io.swagger.v3.oas.models.OpenAPI
import java.math.BigDecimal

private class Bound(val value: BigDecimal, val exclusive: Boolean, keyword: String, suffix: String = "") {
    val description = "$keyword ${value.toPlainString()}$suffix"
}

/**
 * Schemas whose bounds leave no valid value: a lower bound above the upper one, or equal bounds with an exclusive
 * side. Branches of `not`, `anyOf` and `oneOf` count too, since an empty branch is a mistake in the spec.
 */
internal fun contradictoryConstraintErrors(spec: OpenAPI): List<String> {
    val errors = mutableListOf<String>()
    spec.walkSchemas { node ->
        val schema = node.schema
        fun error(problem: String) = errors.add("contradictory constraints in '${node.location}': $problem")

        // The tightest bound wins; on equal values an exclusive bound is the tighter one.
        val lower = listOfNotNull(
            // 3.0 marks `minimum` exclusive with a boolean, 3.1 gives `exclusiveMinimum` its own number.
            schema.minimum?.let {
                val exclusive = schema.exclusiveMinimum == true
                Bound(it, exclusive, "minimum", if (exclusive) " (exclusiveMinimum: true)" else "")
            },
            schema.exclusiveMinimumValue?.let { Bound(it, true, "exclusiveMinimum") },
        ).maxWithOrNull(compareBy<Bound> { it.value }.thenBy { it.exclusive })
        val upper = listOfNotNull(
            schema.maximum?.let {
                val exclusive = schema.exclusiveMaximum == true
                Bound(it, exclusive, "maximum", if (exclusive) " (exclusiveMaximum: true)" else "")
            },
            schema.exclusiveMaximumValue?.let { Bound(it, true, "exclusiveMaximum") },
        ).minWithOrNull(compareBy<Bound> { it.value }.thenBy { !it.exclusive })
        if (lower != null && upper != null) {
            val cmp = lower.value.compareTo(upper.value)
            if (cmp > 0) error("${lower.description} is above ${upper.description}")
            else if (cmp == 0 && (lower.exclusive || upper.exclusive)) error("${lower.description} equals ${upper.description}")
        }

        fun checkRange(minKeyword: String, min: Int?, maxKeyword: String, max: Int?) {
            if (min != null && max != null && min > max) error("$minKeyword $min is above $maxKeyword $max")
        }
        checkRange("minLength", schema.minLength, "maxLength", schema.maxLength)
        checkRange("minItems", schema.minItems, "maxItems", schema.maxItems)
        checkRange("minProperties", schema.minProperties, "maxProperties", schema.maxProperties)
    }
    return errors
}
