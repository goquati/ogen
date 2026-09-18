package de.quati.ogen.client.ktor

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.buildUrl
import io.ktor.http.takeFrom
import kotlinx.serialization.json.Json

public interface HttpClientOgen {
    public val httpClient: HttpClient
    public val baseUrl: String
    public val baseModifier: HttpRequestBuilder.() -> Unit
    public val json: Json

    public class Base(
        public override val httpClient: HttpClient,
        public override val baseUrl: String,
        public override val baseModifier: HttpRequestBuilder.() -> Unit = {},
        public override val json: Json = Json {
            encodeDefaults = false      // omit Undefined (defaults)
            explicitNulls = true        // keep "field": null when Some(null)
        },
    ): HttpClientOgen

    public fun buildUrl(path: String, params: Map<String, String>): Url = buildUrl {
        takeFrom(baseUrl)
        val segments = path.trimStart('/').split('/').map { segment ->
            var segment = segment
            for ((k, v) in params)
                segment = segment.replace("{$k}", v)
            segment
        }
        appendPathSegments(segments, encodeSlash = true)
    }
}
