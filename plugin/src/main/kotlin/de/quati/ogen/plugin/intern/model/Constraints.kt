package de.quati.ogen.plugin.intern.model

import java.math.BigDecimal

/** The validation keywords of a schema that the generated code can check; `null` where the schema sets none. */
internal data class Constraints(
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val pattern: String? = null,
    val minimum: Bound? = null,
    val maximum: Bound? = null,
    val multipleOf: BigDecimal? = null,
    val minItems: Int? = null,
    val maxItems: Int? = null,
    val uniqueItems: Boolean = false,
    val minProperties: Int? = null,
    val maxProperties: Int? = null,
) {
    data class Bound(val value: BigDecimal, val inclusive: Boolean)

    val isEmpty get() = this == NONE

    companion object {
        val NONE = Constraints()

        /** Of two lower bounds, the one fewer values satisfy. */
        fun tighterMinimum(a: Bound?, b: Bound?): Bound? = when {
            a == null -> b
            b == null -> a
            a.value.compareTo(b.value) != 0 -> if (a.value > b.value) a else b
            else -> if (a.inclusive) b else a
        }

        /** Of two upper bounds, the one fewer values satisfy. */
        fun tighterMaximum(a: Bound?, b: Bound?): Bound? = when {
            a == null -> b
            b == null -> a
            a.value.compareTo(b.value) != 0 -> if (a.value < b.value) a else b
            else -> if (a.inclusive) b else a
        }
    }
}
