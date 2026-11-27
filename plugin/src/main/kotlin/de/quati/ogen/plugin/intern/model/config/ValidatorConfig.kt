package de.quati.ogen.plugin.intern.model.config

import de.quati.ogen.plugin.NameConvention
import de.quati.ogen.plugin.Severity
import de.quati.ogen.plugin.ValidatorRule

internal data class ValidatorConfig(
    val parameterFormat: ParameterFormat,
    val tagFormat: NameConvention,
    val operationIdFormat: NameConvention,
    val propertyNameFormat: NameConvention,
    val schemaNameFormat: NameConvention,
    val pathSegmentFormat: NameConvention,
    val stringEnumFormat: NameConvention,
    /** The severities `rules {}` sets; the other rules have their default. */
    val rules: Map<ValidatorRule, Severity> = emptyMap(),
) {
    data class ParameterFormat(
        val path: NameConvention,
        val query: NameConvention,
        val header: NameConvention,
        val cookie: NameConvention,
    )

    fun severity(rule: ValidatorRule): Severity = rules[rule] ?: rule.default
}
