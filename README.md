# ogen - OpenAPI Generator for Kotlin

`ogen` is a Gradle plugin that generates Kotlin code from OpenAPI specifications. It is designed to be idiomatic, supporting both Kotlin Multiplatform (KMP) and modern server/client frameworks.

## Features

- **Type-safe Models**: Generates Kotlin data classes and enums with [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) support.
- **Shared Models**: Core feature allowing models to be generated in a separate Gradle module, shared between server and client.
- **Advanced OpenAPI Support**: Supports OpenAPI 3.0 and 3.1, including `oneOf` (via sealed interfaces), `allOf`, `anyOf`, and complex nested schemas.
- **Kotlin Multiplatform**: Seamlessly works with KMP projects, generating code into the appropriate source sets.
- **Spring Boot Support**: Generates server interfaces for Spring Boot (WebFlux).
- **Ktor Support**: Supports both Ktor Server (planned) and Ktor Client generators.
- **OpenAPI Validation**: Built-in validator to ensure your OpenAPI specs follow best practices and naming conventions.
- **Highly Configurable**: Custom type mappings, schema mappings, and naming convention enforcement.

## Installation

Apply the plugin in your `build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") // or kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("de.quati.ogen") version "0.13.1"

    implementation("de.quati.ogen:core:0.13.1")
    implementation("de.quati.ogen:client-ktor:0.13.1") // Optional: only for generate Ktor clients required
    implementation("de.quati.ogen:server-spring:0.13.1") // Optional: only for Spring server with `apiResponse = true` required
}
```

## Configuration

Configure the generator using the `ogen` extension:

```kotlin
ogen {
    utilPackageName("com.example.api.gen.util")
    add(packageName = "com.example.api.gen") {
        specFile("$projectDir/specs/api.yaml")
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
            // Optional: Return typed ApiResponse classes instead of ResponseEntity (requires de.quati.ogen:server-spring)
            apiResponse = true
        }

        // Optional: Generate Ktor Client
        ktorClient {}
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

### Spring Boot Typed Responses

With `apiResponse = true`, every controller function returns a generated response type implementing
`de.quati.ogen.server.spring.ApiResponse` instead of a `ResponseEntity`. If an operation defines multiple responses,
a sealed interface with one class per response is generated; for a single response it is a single class. Response
classes are data classes, except for streamed (`Flow`) and untyped (e.g. binary) bodies, which only have identity
equality.
Every response class accepts optional `headers`; status ranges (`5XX`) and `default` responses take the `status` as
parameter. Responses with a body also carry their content types from the spec, which are used for the
content negotiation instead of the `produces` of the request mapping, so error responses can use a different content
type (e.g. `application/json`) than the success response (e.g. `image/png`).

```kotlin
suspend fun getUser(userId: String): GetUserResponse

sealed interface GetUserResponse : ApiResponse {
    data class Ok(override val body: UserDto, override val headers: HttpHeaders = HttpHeaders.EMPTY) : GetUserResponse {
        override val status: HttpStatusCode = HttpStatus.OK
    }
    data class Unauthorized(override val headers: HttpHeaders = HttpHeaders.EMPTY) : GetUserResponse {
        override val status: HttpStatusCode = HttpStatus.UNAUTHORIZED
    }
    data class NotFound(override val body: ErrorDto, override val headers: HttpHeaders = HttpHeaders.EMPTY) : GetUserResponse {
        override val status: HttpStatusCode = HttpStatus.NOT_FOUND
    }
}
```

The responses are written by `ApiResponseResultHandler` (a WebFlux `HandlerResultHandler`) from `server-spring`, which
is registered by Spring Boot auto-configuration in reactive web applications. To customize it, define your own
`ApiResponseResultHandler` bean; without Spring Boot, register it manually.

## Tasks

The plugin registers the following tasks:

- `ogenGenerate`: Generates Kotlin code from the configured OpenAPI specifications. This task is automatically hooked into the Kotlin compilation process.
- `ogenValidate`: Validates the OpenAPI specifications against the configured rules without generating code.

## Requirements

- JDK 21 or higher
- Kotlin 2.0 or higher

## License

This project is licensed under the [MIT License](LICENSE).
