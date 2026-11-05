package de.quati.ogen.plugin.intern.validation

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.security.SecurityRequirement

/** Security requirements naming a scheme that `components.securitySchemes` doesn't define. */
internal fun undefinedSecuritySchemeErrors(spec: OpenAPI): List<String> {
    val defined = spec.components?.securitySchemes?.keys.orEmpty()

    fun List<SecurityRequirement>?.errors(location: String) = orEmpty()
        .flatMap { it.keys }
        .distinct()
        .filter { it !in defined }
        .map { "security scheme '$it' in $location is not defined in components.securitySchemes" }

    return spec.security.errors("the top-level security") +
        spec.operations().flatMap { it.operation.security.errors("'${it.location}'") }
}
