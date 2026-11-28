# Spring Boot Server

`serverSpringV4 {}` generates one `@RestController` interface per tag for Spring Boot 4 with WebFlux (Spring WebMVC is
not supported). The functions are `suspend` functions; implement the interface in a Spring bean.

```kotlin
serverSpringV4 {
    // Adds an OperationContext parameter (containing meta-info about the endpoint) to each generated function
    addOperationContext = true
    // If the operation has any security requirements, add the specified class as a parameter
    contextIfAnySecurity("com.example.api.AuthContext")
    // Return typed ApiResponse classes instead of ResponseEntity (requires de.quati.ogen:server-spring)
    apiResponse = true
}
```

## Security context

When using `contextIfAnySecurity`, you must provide a custom `HandlerMethodArgumentResolver` to Spring Boot so it knows
how to inject your context class into the controller methods.

Example registration in a `WebFluxConfigurer`:

```kotlin
@Configuration
class WebConfig : WebFluxConfigurer {
    override fun configureArgumentResolvers(configurer: ArgumentResolverConfigurer) {
        configurer.addCustomResolver(AuthContext.ArgumentResolver)
    }
}
```

## Typed responses

With `apiResponse = true`, every controller function returns a generated response type implementing
`de.quati.ogen.server.spring.ApiResponse` instead of a `ResponseEntity`. If an operation defines multiple responses,
a sealed interface with one class per response is generated; for a single response it is a single class. Response
classes are data classes, except for streamed (`Flow`) and untyped (e.g. binary) bodies, which only have identity
equality.

Every response class accepts optional `headers`; status ranges (`5XX`) and `default` responses take the `status` as
parameter. Responses with a body also carry their content types from the spec, which are used for the content
negotiation instead of the `produces` of the request mapping, so error responses can use a different content type
(e.g. `application/json`) than the success response (e.g. `image/png`).

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

## Request bodies

### Multipart

`multipart/form-data` request bodies, including file uploads, are supported in the Spring server and the Ktor client.

- A body whose parts aren't all known (an object with `additionalProperties`, or no object) comes to the Spring
  server as `MultiValueMap<String, Part>`.
- In an optional multipart body (`required: false`), every part is optional, as the whole body may be left out. The
  Spring server then accepts a present body without a required part, and a body of another media type as a left-out
  one.

### Other media types

A request body that is neither JSON nor multipart (e.g. `application/octet-stream`) comes to the Spring server as
`ByteArray`, or as `String` when all its media types are `text/*`. WebFlux reads it fully into memory, up to
`spring.codec.max-in-memory-size` (256 KB by default); a larger body fails with `DataBufferLimitException` unless that
limit is raised.

## Bean Validation

With `beanValidation = true`, the controller parameters get the constraints of their schemas and request bodies get
`@Valid`; Spring answers invalid requests with `400 Bad Request`. This needs `spring-boot-starter-validation`. See
[Bean Validation](models.md#bean-validation).

## The QUERY method

OpenAPI 3.2 specs may have a `query` operation in a Path Item: the HTTP QUERY method, a safe and idempotent request
with a body. In 3.0 and 3.1 specs, `query` is an unknown field, reported as an error and not generated.

- The Ktor client sends it with `HttpMethod("QUERY")`, as Ktor has no constant for it.
- The Spring server maps it with `RequestMethod.QUERY`, which needs Spring Framework 7.1 or higher. Spring Security's
  firewall rejects the method with `400 Bad Request` unless it is allowed:

```kotlin
@Bean
fun serverWebExchangeFirewall() = StrictServerWebExchangeFirewall().apply {
    setAllowedHttpMethods(listOf(GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS, HttpMethod.QUERY))
}
```
