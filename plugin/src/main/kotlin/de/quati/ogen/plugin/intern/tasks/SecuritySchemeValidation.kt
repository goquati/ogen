package de.quati.ogen.plugin.intern.tasks

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.security.SecurityRequirement
import org.gradle.api.GradleException
import org.slf4j.Logger

internal fun validateSecuritySchemesDefined(spec: OpenAPI, logger: Logger) {
    val errors = getUndefinedSecuritySchemeErrors(spec)
    if (errors.isEmpty()) return
    errors.forEach { logger.error("\t$it") }
    throw GradleException("Found ${errors.size} undefined security scheme references.")
}

internal fun getUndefinedSecuritySchemeErrors(spec: OpenAPI): List<String> {
    val defined = spec.components?.securitySchemes?.keys.orEmpty()

    fun List<SecurityRequirement>?.check(location: String) = orEmpty()
        .flatMap { it.keys }
        .distinct()
        .filter { it !in defined }
        .map { "security scheme '$it' in $location is not defined in components.securitySchemes" }

    return spec.security.check("top-level security") +
            spec.paths.orEmpty().flatMap { (path, pathItem) ->
                pathItem.readOperationsMap().flatMap { (method, operation) ->
                    operation.security.check("'$path:${method.name}'")
                }
            }
}
