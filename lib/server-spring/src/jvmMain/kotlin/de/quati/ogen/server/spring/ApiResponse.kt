package de.quati.ogen.server.spring

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType

/**
 * Typed response of a generated Spring WebFlux controller function.
 *
 * Written to the HTTP response by [ApiResponseResultHandler]. The declared (non-bridge) return type of the
 * implementing class' `body` getter is used as body type for the message writers, so generic bodies like
 * `List<T>` or `Flow<T>` are serialized correctly.
 */
public interface ApiResponse {
    public val status: HttpStatusCode
    public val body: Any? get() = null
    public val headers: HttpHeaders get() = HttpHeaders.EMPTY

    /**
     * Content types this response can be written as. Replaces the `produces` of the request mapping for the content
     * negotiation of the body, so e.g. error responses can use other content types than the success response.
     * Empty means no restriction.
     */
    public val contentTypes: List<MediaType> get() = emptyList()
}
