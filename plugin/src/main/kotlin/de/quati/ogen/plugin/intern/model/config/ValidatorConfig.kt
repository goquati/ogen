package de.quati.ogen.plugin.intern.model.config

import de.quati.ogen.plugin.NameConvention
import de.quati.ogen.plugin.Severity
import de.quati.ogen.plugin.ValidatorRule

internal data class ValidatorConfig(
    val naming: Naming,
    /** The severities `rules {}` sets; the other rules have their default. */
    val rules: Map<ValidatorRule, Severity> = emptyMap(),
) {
    data class Naming(
        val schema: NameConvention,
        val property: NameConvention,
        val tag: NameConvention,
        val operationId: NameConvention,
        val pathSegment: NameConvention,
        val enumValue: NameConvention,
        val parameter: Parameter,
    ) {
        data class Parameter(
            val path: NameConvention,
            val query: NameConvention,
            val header: NameConvention,
            val cookie: NameConvention,
        )
    }

    fun severity(rule: ValidatorRule): Severity = rules[rule] ?: rule.default
}
