package de.quati.ogen.plugin.intern.validation

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema
import java.math.BigDecimal

private class Bound(val value: BigDecimal, val exclusive: Boolean, keyword: String, suffix: String = "") {
    val description = "$keyword ${value.toPlainString()}$suffix"
}

// 3.0 marks `minimum` exclusive with a boolean, 3.1 gives `exclusiveMinimum` its own number.
private fun Schema<*>.lowerBounds() = listOfNotNull(
    minimum?.let {
        val exclusive = exclusiveMinimum == true
        Bound(it, exclusive, "minimum", if (exclusive) " (exclusiveMinimum: true)" else "")
    },
    exclusiveMinimumValue?.let { Bound(it, true, "exclusiveMinimum") },
)

private fun Schema<*>.upperBounds() = listOfNotNull(
    maximum?.let {
        val exclusive = exclusiveMaximum == true
        Bound(it, exclusive, "maximum", if (exclusive) " (exclusiveMaximum: true)" else "")
    },
    exclusiveMaximumValue?.let { Bound(it, true, "exclusiveMaximum") },
)

// Ordered so that maxWith picks the tighter bound: the greater lower, the smaller upper; on equal values the exclusive one.
private val tighterLower = compareBy<Bound> { it.value }.thenBy { it.exclusive }
private val tighterUpper = compareByDescending<Bound> { it.value }.thenBy { it.exclusive }

/**
 * Schemas whose bounds leave no valid value: a lower bound above the upper one, or equal bounds with an exclusive
 * side. Branches of `not`, `anyOf` and `oneOf` count too, since an empty branch is a mistake in the spec.
 */
internal fun contradictoryConstraintErrors(spec: OpenAPI): List<String> {
    val errors = mutableListOf<String>()
    spec.walkSchemas { node ->
        val schema = node.schema
        fun error(problem: String) = errors.add("contradictory constraints in '${node.location}': $problem")

        val lower = schema.lowerBounds().maxWithOrNull(tighterLower)
        val upper = schema.upperBounds().maxWithOrNull(tighterUpper)
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

/**
 * OpenAPI 3.1 schemas with both `minimum` and `exclusiveMinimum`, or both `maximum` and `exclusiveMaximum`: the tighter
 * bound applies and the other has no effect. 3.0's boolean `exclusiveMinimum` modifies `minimum`, so it never doubles.
 */
internal fun redundantBoundWarnings(spec: OpenAPI): List<String> {
    val warnings = mutableListOf<String>()
    spec.walkSchemas { node ->
        listOf(node.schema.lowerBounds() to tighterLower, node.schema.upperBounds() to tighterUpper)
            .filter { (bounds, _) -> bounds.size == 2 }
            .forEach { (bounds, tighter) ->
                val effective = bounds.maxWith(tighter)
                val ignored = bounds.single { it !== effective }
                warnings.add("${ignored.description} has no effect next to ${effective.description} in '${node.location}'")
            }
    }
    return warnings
}
