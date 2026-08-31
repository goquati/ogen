package de.quati.ogen.client.ktor

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.buildUrl
import io.ktor.http.takeFrom
import io.ktor.utils.io.readLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

public class HttpClientOgen(
    public val httpClient: HttpClient,
    public val baseUrl: String,
    public val baseModifier: HttpRequestBuilder.() -> Unit = {},
    public val json: Json = Json {
        encodeDefaults = false      // omit Undefined (defaults)
        explicitNulls = true        // keep "field": null when Some(null)
    },
) {
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

    public inline fun <reified T> bodyAsFlow(stmt: HttpStatement): Flow<T> = flow {
        val res = stmt.execute()
        val channel = res.bodyAsChannel()
        while (true) {
            val line = channel.readLine() ?: break
            val lineObject = json.decodeFromString<T>(line)
            emit(lineObject)
        }
    }

    public suspend fun <T> use(block: suspend (HttpClientOgen) -> T): T = httpClient.use {
        block(this)
    }
}
