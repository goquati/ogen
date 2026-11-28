package de.quati.ogen.plugin.intern.validation

import de.quati.ogen.plugin.intern.oas.model.Schema
import java.math.BigDecimal

private class Bound(val value: BigDecimal, val exclusive: Boolean, val description: String)

// Messages name the bounds as written: in 3.0 an exclusive bound is `minimum` with `exclusiveMinimum: true`.
private fun Schema.Obj.lowerBounds(is30: Boolean) = listOfNotNull(
    minimum?.let { Bound(it, false, "minimum ${it.toPlainString()}") },
    exclusiveMinimum?.let {
        Bound(it, true, if (is30) "minimum ${it.toPlainString()} (exclusiveMinimum: true)" else "exclusiveMinimum ${it.toPlainString()}")
    },
)

private fun Schema.Obj.upperBounds(is30: Boolean) = listOfNotNull(
    maximum?.let { Bound(it, false, "maximum ${it.toPlainString()}") },
    exclusiveMaximum?.let {
        Bound(it, true, if (is30) "maximum ${it.toPlainString()} (exclusiveMaximum: true)" else "exclusiveMaximum ${it.toPlainString()}")
    },
)

// Ordered so that maxWith picks the tighter bound: the greater lower, the smaller upper; on equal values the exclusive one.
private val tighterLower = compareBy<Bound> { it.value }.thenBy { it.exclusive }
private val tighterUpper = compareByDescending<Bound> { it.value }.thenBy { it.exclusive }

/**
 * Schemas whose bounds leave no valid value: a lower bound above the upper one, or equal bounds with an exclusive
 * side. Branches of `not`, `anyOf` and `oneOf` count too, since an empty branch is a mistake in the spec.
 */
internal fun contradictoryConstraintErrors(spec: ValidatedSpec): List<String> {
    val errors = mutableListOf<String>()
    spec.walkSchemas { node ->
        val schema = node.schema
        fun error(problem: String) = errors.add("contradictory constraints in '${node.location}': $problem")

        val lower = schema.lowerBounds(spec.is30).maxWithOrNull(tighterLower)
        val upper = schema.upperBounds(spec.is30).maxWithOrNull(tighterUpper)
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
        val required = schema.required.orEmpty().distinct().size
        val maxProperties = schema.maxProperties
        if (maxProperties != null && required > maxProperties)
            error("$required required properties are above maxProperties $maxProperties")
    }
    return errors
}

/**
 * Schemas with both `minimum` and `exclusiveMinimum`, or both `maximum` and `exclusiveMaximum` (3.1 and later): the
 * tighter bound applies and the other has no effect. In 3.0 the boolean `exclusiveMinimum` modifies `minimum`, so
 * migration leaves only one of them.
 */
internal fun redundantBoundWarnings(spec: ValidatedSpec): List<String> {
    val warnings = mutableListOf<String>()
    spec.walkSchemas { node ->
        listOf(node.schema.lowerBounds(spec.is30) to tighterLower, node.schema.upperBounds(spec.is30) to tighterUpper)
            .filter { (bounds, _) -> bounds.size == 2 }
            .forEach { (bounds, tighter) ->
                val effective = bounds.maxWith(tighter)
                val ignored = bounds.single { it !== effective }
                warnings.add("${ignored.description} has no effect next to ${effective.description} in '${node.location}'")
            }
    }
    return warnings
}
