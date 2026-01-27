package de.quati.ogen.plugin.intern.model.config

import de.quati.ogen.plugin.NameConvention

internal data class ValidatorConfig(
    val failOnWarnings: Boolean,
    val recommendations: Boolean,
    val parameterFormat: ParameterFormat,
    val tagFormat: NameConvention,
    val operationIdFormat: NameConvention,
    val propertyNameFormat: NameConvention,
    val schemaNameFormat: NameConvention,
    val pathSegmentFormat: NameConvention,
    val stringEnumFormat: NameConvention,
) {
    data class ParameterFormat(
        val path: NameConvention,
        val query: NameConvention,
        val header: NameConvention,
        val cookie: NameConvention,
    )
}
