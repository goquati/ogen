package de.quati.ogen

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.web.reactive.config.WebFluxConfigurer
import org.springframework.web.reactive.result.method.annotation.ArgumentResolverConfigurer

@Configuration
class WebConfig : WebFluxConfigurer {
    override fun configureArgumentResolvers(configurer: ArgumentResolverConfigurer) {
        configurer.addCustomResolver(AuthContext.ArgumentResolver)
    }

    @Bean
    @Suppress("DEPRECATION")
    fun userDetailsService(): MapReactiveUserDetailsService = MapReactiveUserDetailsService(
        User.withDefaultPasswordEncoder()
            .username("testUser")
            .password("123")
            .roles("USER")
            .build(),
        User.withDefaultPasswordEncoder()
            .username("testAdmin")
            .password("456")
            .roles("ADMIN")
            .build(),
    )
}

@Configuration
class SecurityConfig {
    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain = http
        .csrf { it.disable() }
        .authorizeExchange {
            it.pathMatchers("/api/v1/public/**").permitAll()
            it.anyExchange().authenticated()
        }
        .httpBasic { }
        .addFilterAt(AuthContext.WebFilter, SecurityWebFiltersOrder.AUTHORIZATION)
        .build()
}