package de.quati.ogen.server.spring

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.core.ReactiveAdapterRegistry
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.web.reactive.accept.RequestedContentTypeResolver

/**
 * Registers the [ApiResponseResultHandler] in reactive Spring Boot applications.
 *
 * Without Spring Boot, register an [ApiResponseResultHandler] bean manually.
 */
@AutoConfiguration(afterName = ["org.springframework.boot.webflux.autoconfigure.WebFluxAutoConfiguration"])
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class ApiResponseAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public fun ogenApiResponseResultHandler(
        serverCodecConfigurer: ServerCodecConfigurer,
        @Qualifier("webFluxContentTypeResolver") contentTypeResolver: RequestedContentTypeResolver,
        @Qualifier("webFluxAdapterRegistry") adapterRegistry: ReactiveAdapterRegistry,
    ): ApiResponseResultHandler = ApiResponseResultHandler(
        writers = serverCodecConfigurer.writers,
        resolver = contentTypeResolver,
        registry = adapterRegistry,
    )
}
