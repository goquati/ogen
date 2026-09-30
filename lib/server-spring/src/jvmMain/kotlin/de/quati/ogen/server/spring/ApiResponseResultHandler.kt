package de.quati.ogen.server.spring

import org.springframework.core.MethodParameter
import org.springframework.core.ReactiveAdapterRegistry
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.codec.HttpMessageWriter
import org.springframework.web.reactive.HandlerResult
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.HandlerResultHandler
import org.springframework.web.reactive.accept.RequestedContentTypeResolver
import org.springframework.web.reactive.result.method.annotation.AbstractMessageWriterResultHandler
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.lang.reflect.Method
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * [HandlerResultHandler] that writes [ApiResponse] return values (status, headers and body) of controller functions.
 *
 * Supports plain, `suspend` and `Mono` returning handler functions. Like Spring's `ResponseEntityResultHandler`,
 * `GET`/`HEAD` responses with a body answer conditional requests with `304 Not Modified` based on the `ETag` and
 * `Last-Modified` response headers. The default order is higher than the one of
 * Spring's `ResponseBodyResultHandler`, which would otherwise serialize the [ApiResponse] itself as body.
 */
public class ApiResponseResultHandler(
    writers: List<HttpMessageWriter<*>>,
    resolver: RequestedContentTypeResolver,
    registry: ReactiveAdapterRegistry = ReactiveAdapterRegistry.getSharedInstance(),
) : AbstractMessageWriterResultHandler(writers, resolver, registry), HandlerResultHandler {
    private val bodyParameters = ConcurrentHashMap<Class<*>, MethodParameter>()

    init {
        order = HIGHEST_PRECEDENCE + 100
    }

    override fun supports(result: HandlerResult): Boolean {
        val type = result.returnType
        if (type.toClass().isApiResponse) return true
        if (type.toClass() == Any::class.java && result.returnValue is ApiResponse) return true
        val adapter = getAdapter(result) ?: return false
        return !adapter.isMultiValue && !adapter.isNoValue && type.getGeneric().toClass().isApiResponse
    }

    override fun handleResult(exchange: ServerWebExchange, result: HandlerResult): Mono<Void> {
        val responseMono: Mono<*> = when (val value = result.returnValue) {
            null -> Mono.empty<Any>()
            is ApiResponse -> Mono.just(value)
            else -> {
                val adapter = getAdapter(result)
                    ?: return Mono.error(IllegalStateException("unsupported return value ${value::class.java}"))
                Mono.from(adapter.toPublisher(value))
            }
        }
        return responseMono.flatMap { response ->
            if (response !is ApiResponse)
                return@flatMap Mono.error(IllegalArgumentException("ApiResponse expected but got: ${response::class.java}"))
            writeResponse(exchange, response)
        }
    }

    private fun writeResponse(exchange: ServerWebExchange, response: ApiResponse): Mono<Void> {
        exchange.response.statusCode = response.status
        val headers = response.headers
        if (!headers.isEmpty) exchange.response.headers.putAll(headers)
        val body = response.body ?: return exchange.response.setComplete()
        if (exchange.isNotModified(headers)) return exchange.response.setComplete()
        if (response.contentTypes.isNotEmpty())
            exchange.attributes[HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE] = response.contentTypes.toSet()
        return writeBody(body, bodyParameter(response::class.java), exchange)
    }

    private fun bodyParameter(clazz: Class<*>) = bodyParameters.getOrPut(clazz) {
        MethodParameter(clazz.bodyGetter(), -1)
    }

    private companion object {
        private val SAFE_METHODS = setOf(HttpMethod.GET, HttpMethod.HEAD)
        private val Class<*>.isApiResponse get() = ApiResponse::class.java.isAssignableFrom(this)

        /** conditional request handling (`If-None-Match` / `If-Modified-Since`), sets status 304 if not modified */
        private fun ServerWebExchange.isNotModified(headers: HttpHeaders) =
            request.method in SAFE_METHODS &&
                    checkNotModified(headers.eTag, Instant.ofEpochMilli(headers.lastModified))

        private fun Class<*>.bodyGetter(): Method = methods
            .filter { it.name == "getBody" && it.parameterCount == 0 && !it.isBridge && !it.isSynthetic }
            .reduceOrNull { a, b -> if (a.returnType.isAssignableFrom(b.returnType)) b else a }
            ?: ApiResponse::class.java.getMethod("getBody")
    }
}
