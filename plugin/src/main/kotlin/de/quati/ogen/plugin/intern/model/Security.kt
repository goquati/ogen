package de.quati.ogen.plugin.intern.model

import io.swagger.v3.oas.models.security.SecurityScheme

internal data class Security(
    val data: List<List<RequirementObject>>,
) {
    val anySecurity get() = data.any { it.isNotEmpty() }
    val noSecurity get() = data.all { it.isEmpty() }

    data class RequirementObject(
        val name: ComponentName.Security,
        val type: SecurityScheme.Type,
    )
}