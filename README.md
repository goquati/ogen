# ogen - OpenAPI Generator for Kotlin

`ogen` is a Gradle plugin that generates Kotlin code from OpenAPI specifications. It is designed to be idiomatic, supporting both Kotlin Multiplatform (KMP) and modern server/client frameworks.

## Features

- **Type-safe Models**: Generates Kotlin data classes and enums with [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) support.
- **Shared Models**: Core feature allowing models to be generated in a separate Gradle module, shared between server and client.
- **Advanced OpenAPI Support**: Supports OpenAPI 3.0 and 3.1, including `oneOf` (via sealed interfaces), `allOf`, `anyOf`, and complex nested schemas.
- **Kotlin Multiplatform**: Seamlessly works with KMP projects, generating code into the appropriate source sets.
- **Spring Boot Support**: Generates server interfaces for Spring Boot (WebFlux).
- **Ktor Support**: Planned support for both Ktor Server and Ktor Client generators.
- **OpenAPI Validation**: Built-in validator to ensure your OpenAPI specs follow best practices and naming conventions.
- **Highly Configurable**: Custom type mappings, schema mappings, and naming convention enforcement.

## Installation

Apply the plugin in your `build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") // or kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("de.quati.ogen") version "0.1.0"
}
```

## Configuration

Configure the generator using the `ogen` extension:

```kotlin
ogen {
    addSpec(
        apiFile = "$projectDir/specs/api.yaml",
        packageName = "com.example.api.gen"
    ) {
        // Optional: Configure validation
        validator {
            failOnWarnings = true
            // Enforce naming conventions
            propertyNameFormat = NameConvention.CamelCase
            schemaNameFormat = NameConvention.PascalCase
        }

        // Optional: Configure model generation
        model {
            // Map OpenAPI types to existing Kotlin/Java classes
            typeMapping(
                type = "string+date-time", clazz = "java.time.OffsetDateTime",
                serializerObject = "com.example.serializers.OffsetDateTimeSerializer"
            )
            // Map specific schemas to existing classes
            schemaMapping(schema = "UserId", clazz = "com.example.models.UserId")
        }

        // Optional: Generate Spring Boot server interfaces
        serverSpringV4 {
            // Adds an OperationContext parameter (containing meta-info about the endpoint) to each generated function
            addOperationContext = true
            // Optional: If the operation has any security requirements, add the specified class as a parameter
            contextIfAnySecurity("com.example.api.AuthContext")
        }
    }
}
```

### Spring Boot Security Context

When using `contextIfAnySecurity`, you must provide a custom `HandlerMethodArgumentResolver` to Spring Boot so it knows how to inject your context class into the controller methods.

Example registration in a `WebFluxConfigurer`:

```kotlin
@Configuration
class WebConfig : WebFluxConfigurer {
    override fun configureArgumentResolvers(configurer: ArgumentResolverConfigurer) {
        configurer.addCustomResolver(AuthContext.ArgumentResolver)
    }
}
```

## Tasks

The plugin registers the following tasks:

- `ogenGenerate`: Generates Kotlin code from the configured OpenAPI specifications. This task is automatically hooked into the Kotlin compilation process.
- `ogenValidate`: Validates the OpenAPI specifications against the configured rules without generating code.

## Requirements

- JDK 21 or higher
- Kotlin 2.0 or higher

## Roadmap

- [ ] Ktor Client generator
- [ ] Ktor Server generator

## License

This project is licensed under the [MIT License](LICENSE).
