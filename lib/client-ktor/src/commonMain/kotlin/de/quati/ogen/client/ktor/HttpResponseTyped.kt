package de.quati.ogen.client.ktor

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.util.reflect.TypeInfo
import io.ktor.util.reflect.typeInfo

public class HttpResponseTyped<T : Any>(
    public val raw: HttpResponse,
    public val bodyTypeInfo: TypeInfo,
) {
    public val status: HttpStatusCode = raw.status

    public suspend fun body(): T {
        val result = raw.body<T>(bodyTypeInfo)
        return result
    }

    public companion object {
        public inline fun <reified T : Any> HttpResponse.toTyped(): HttpResponseTyped<T> = HttpResponseTyped(
            raw = this,
            bodyTypeInfo = typeInfo<T>(),
        )
    }
}
