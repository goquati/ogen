package de.quati.ogen.plugin.intern.validation

import de.quati.ogen.plugin.intern.model.HttpMethod
import de.quati.ogen.plugin.intern.oas.model.Parameter
import de.quati.ogen.plugin.intern.oas.model.RefOr
import de.quati.ogen.plugin.intern.oas.model.SecurityScheme

/** GET and HEAD operations with a request body, which HTTP gives no meaning and many servers and proxies drop. */
internal fun getWithBodyWarnings(spec: ValidatedSpec): List<String> = spec.operations()
    .filter { it.method in setOf(HttpMethod.GET, HttpMethod.HEAD) && it.operation.requestBody != null }
    .map { "operation '${it.location}' has a request body, which ${it.method.name} requests should not have" }

/** Header names with `_`: Apache and Nginx drop such headers by default. */
internal fun headerUnderscoreWarnings(spec: ValidatedSpec): List<String> {
    fun message(name: String, location: String) =
        "header '$name' in '$location' contains '_'; Apache and Nginx drop such headers by default"
    val parameters = spec.parameters()
        .filter { it.parameter.location == Parameter.Location.HEADER && '_' in it.parameter.name }
        .map { message(it.parameter.name, it.ownerLocation) }
    val schemes = spec.api.components?.securitySchemes.orEmpty().mapNotNull { (key, scheme) ->
        val value = (scheme as? RefOr.Value)?.value ?: return@mapNotNull null
        val name = value.name ?: return@mapNotNull null
        if (value.type == SecurityScheme.Type.API_KEY && value.location == SecurityScheme.Location.HEADER && '_' in name)
            message(name, "components.securitySchemes.$key")
        else null
    }
    return parameters + schemes
}
