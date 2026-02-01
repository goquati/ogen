package de.quati.ogen

import org.springframework.core.MethodParameter
import org.springframework.web.reactive.BindingContext
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

data class AuthContext(
    val name: String,
) {
    object ArgumentResolver : HandlerMethodArgumentResolver {
        override fun supportsParameter(p: MethodParameter) = p.parameterType == AuthContext::class.java
        override fun resolveArgument(p: MethodParameter, b: BindingContext, ex: ServerWebExchange): Mono<Any> {
            val ctx = ex.attributes[AuthContext::class.java.name] as? AuthContext
            if (ctx != null) return Mono.just(ctx)
            return ex.getPrincipal<java.security.Principal>()
                .map { AuthContext(it.name) }
                .cast(Any::class.java)
        }
    }

    object WebFilter : org.springframework.web.server.WebFilter {
        override fun filter(
            exchange: ServerWebExchange,
            chain: WebFilterChain,
        ): Mono<Void> = exchange.getPrincipal<java.security.Principal>().map { principal ->
            val ctx = AuthContext(principal.name)
            exchange.attributes[AuthContext::class.java.name] = ctx
            principal
        }.then(chain.filter(exchange))
    }
}
