package de.quati.ogen

import kotlinx.serialization.json.Json
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class JsonConfig {
    @Bean
    fun json(): Json = Json {
        encodeDefaults = false      // omit Undefined (defaults)
        explicitNulls = true        // keep "field": null when Some(null)
    }
}