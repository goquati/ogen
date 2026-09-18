package de.quati.ogen.client.ktor

import de.quati.ogen.core.SecurityRequirement
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.util.AttributeKey
import io.ktor.utils.io.readLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

public val ogenAuthAttr: AttributeKey<List<SecurityRequirement>> = AttributeKey("ogenAuth")

public fun HttpRequestBuilder.getOgenAuthNotes(): List<SecurityRequirement> =
    attributes.getOrNull(ogenAuthAttr) ?: emptyList()

public suspend fun <T, CLIENT : HttpClientOgen> CLIENT.use(
    block: suspend (CLIENT) -> T,
): T = httpClient.use {
    block(this)
}

public inline fun <reified T> HttpClientOgen.bodyAsFlow(stmt: HttpStatement): Flow<T> = flow {
    val res = stmt.execute()
    val channel = res.bodyAsChannel()
    while (true) {
        val line = channel.readLine() ?: break
        val lineObject = json.decodeFromString<T>(line)
        emit(lineObject)
    }
}

public suspend fun <T : Any> HttpResponseTyped<T>.bodyIfSuccessOrNull(): T? =
    takeIf { it.status.isSuccess() }?.body()
