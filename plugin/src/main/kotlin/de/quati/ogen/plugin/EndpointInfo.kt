package de.quati.ogen.plugin

import de.quati.ogen.plugin.intern.model.HttpMethod

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
            internal fun of(method: HttpMethod) = when (method) {
                HttpMethod.POST -> POST
                HttpMethod.GET -> GET
                HttpMethod.PUT -> PUT
                HttpMethod.PATCH -> PATCH
                HttpMethod.DELETE -> DELETE
                HttpMethod.HEAD -> HEAD
                HttpMethod.OPTIONS -> OPTIONS
                HttpMethod.TRACE -> TRACE
            }
        }
    }
}
