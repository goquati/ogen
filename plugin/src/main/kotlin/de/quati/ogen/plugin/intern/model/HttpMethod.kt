package de.quati.ogen.plugin.intern.model

/** The HTTP methods of a Path Item that code is generated for. */
internal enum class HttpMethod(val fieldName: String) {
    GET("get"),
    PUT("put"),
    POST("post"),
    DELETE("delete"),
    PATCH("patch"),
    HEAD("head"),
    OPTIONS("options"),
    TRACE("trace"),
    QUERY("query"),
}
