package de.quati.ogen.plugin

import io.swagger.v3.oas.models.PathItem

public data class EndpointInfo(
    val tagPathPrefix: String,
    val path: String,
    val method: Method,
    val tag: String,
) {
    val relativePath: String
        get() = path.removePrefix(tagPathPrefix)

    public enum class Method {
        POST,
        GET,
        PUT,
        PATCH,
        DELETE,
        HEAD,
        OPTIONS,
        TRACE;

        internal companion object {
            internal fun of(method: PathItem.HttpMethod) = when (method) {
                PathItem.HttpMethod.POST -> POST
                PathItem.HttpMethod.GET -> GET
                PathItem.HttpMethod.PUT -> PUT
                PathItem.HttpMethod.PATCH -> PATCH
                PathItem.HttpMethod.DELETE -> DELETE
                PathItem.HttpMethod.HEAD -> HEAD
                PathItem.HttpMethod.OPTIONS -> OPTIONS
                PathItem.HttpMethod.TRACE -> TRACE
            }
        }
    }
}
