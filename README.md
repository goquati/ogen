# ogen - OpenAPI Generator for Kotlin

`ogen` is a Gradle plugin that generates Kotlin code from OpenAPI specifications. It is designed to be idiomatic,
supporting both Kotlin Multiplatform (KMP) and modern server/client frameworks.

## Features

- **Type-safe Models**: Kotlin data classes and enums with
  [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) support.
- **Shared Models**: models can be generated in a separate Gradle module, shared between server and client.
- **Advanced OpenAPI Support**: OpenAPI 3.0, 3.1 and 3.2, including `oneOf` (via sealed interfaces), `allOf`,
  `anyOf`, and complex nested schemas.
- **Kotlin Multiplatform**: generates code into the appropriate source sets of KMP projects.
- **Spring Boot Server**: server interfaces for Spring Boot 4 (WebFlux), optionally with typed responses.
- **Ktor Client**: Ktor clients for every platform (a Ktor server generator is planned).
- **Bean Validation**: optional Jakarta Bean Validation annotations from the schemas' validation keywords.
- **Multipart and binary bodies**: `multipart/form-data` (including file uploads), binary and text request bodies.
- **OpenAPI Validation**: a built-in validator checks that specs are valid and follow best practices and naming
  conventions.
- **Highly Configurable**: custom type mappings, schema mappings, and naming convention enforcement.

## Quickstart

Put a spec into `specs/api.yaml`:

```yaml
openapi: 3.1.0
info: { title: Users, version: 1.0.0 }
paths:
  /users/{userId}:
    get:
      tags: [users]
      operationId: getUser
      parameters:
        - { name: userId, in: path, required: true, schema: { type: string } }
      responses:
        "200":
          description: The user
          content:
            application/json:
              schema: { $ref: "#/components/schemas/User" }
components:
  schemas:
    User:
      type: object
      required: [id, name]
      properties:
        id: { type: string }
        name: { type: string }
```

Apply the plugin (see [Installation](#installation) for the dependencies) and configure what to generate:

```kotlin
ogen {
    utilPackageName("com.example.api.gen.util")
    add(packageName = "com.example.api.gen") {
        specFile("$projectDir/specs/api.yaml")
        model {}
        clientKtor {}       // and/or serverSpringV4 {}
    }
}
```

The next build generates `UserDto` in `com.example.api.gen.model` and one API class per tag: `UsersApi` in
`com.example.api.gen.client` for the Ktor client, and in `com.example.api.gen.server` for the Spring server.

Calling the API with the Ktor client:

```kotlin
import com.example.api.gen.client.UsersApi.Companion.usersApi
import de.quati.ogen.client.ktor.HttpClientOgen

val client = HttpClientOgen.Base(
    httpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json() }
    },
    baseUrl = "https://api.example.com",
)

val response = client.usersApi.getUser(userId = "42")   // HttpResponseTyped<UserDto>
val user: UserDto = response.body()
```

Implementing it with Spring Boot:

```kotlin
@Service
class UsersController : UsersApi {
    override suspend fun getUser(userId: String): ResponseEntity<UserDto> =
        ResponseEntity.ok(UserDto(id = userId, name = "Jane"))
}
```

## Installation

Apply the plugin and add the runtime libraries in your `build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") // or kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("de.quati.ogen") version "0.17.0"
}

dependencies {
    implementation("de.quati.ogen:core:0.17.0")
    implementation("de.quati.ogen:client-ktor:0.17.0") // Optional: only required for generated Ktor clients
    implementation("de.quati.ogen:server-spring:0.17.0") // Optional: only required for the Spring server with `apiResponse = true`
    implementation("de.quati:kotlin-util:2.6.1") // Provides `Option`, used by generated models for optional properties
}
```

The generated code also needs `kotlinx-serialization-json`. If your spec uses `format: uuid`, the generated code uses
`kotlin.uuid.Uuid`, which requires opting in to `kotlin.uuid.ExperimentalUuidApi`:

```kotlin
kotlin {
    compilerOptions {
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }
}
```

## Configuration

Configure the generator using the `ogen` extension. `utilPackageName`, a spec (`specFile` or `specMerge`) and `model`
are required; everything else is optional.

```kotlin
ogen {
    utilPackageName("com.example.api.gen.util")
    add(packageName = "com.example.api.gen") {
        specFile("$projectDir/specs/api.yaml")
        // Optional: Validator settings; without this block, the spec is validated with the defaults
        validator {
            naming {
                schema = NameConvention.PascalCase
                property = NameConvention.CamelCase
            }
            // Fail the build on any finding, except unused schemas
            rules(RuleSet.Strict) {
                unusedSchemas = Severity.Off
            }
        }

        // Optional: Annotate the models and the Spring controller parameters with Jakarta Bean Validation constraints
        beanValidation = true

        // Required: Generate models
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
            addOperationContext = true
            contextIfAnySecurity("com.example.api.AuthContext")
            apiResponse = true
        }

        // Optional: Generate Ktor Client
        clientKtor {}
    }
}
```

### Further Options

- **Multiple specs**: call `add` once per spec, each with its own package name.
- **Package names and postfixes**: by default, models go to `<packageName>.model` with the postfix `Dto`, the Spring
  server to `<packageName>.server` and the Ktor client to `<packageName>.client`, both with the postfix `Api`. Each of
  `model`, `serverSpringV4` and `clientKtor` can override this with `packageName` and `postfix`.
- **Type and schema mappings**: `typeMapping` takes `type` as `<type>` or `<type>+<format>` (e.g. `string+date-time`).
  `schemaMapping` takes a schema name; nested schemas are separated by dots. `serializerObject` is optional for both.
- **Merging specs**: instead of `specFile`, `specMerge(path = "$projectDir/specs")` merges all specs in a directory.
  The merged spec's `info` is set with `mergedFileInfoTitle`, `mergedFileInfoVersion` and `mergedFileInfoDescription`,
  or taken from a base spec with `specMerge(path = ..., baseSpec = "$projectDir/specs/base.yaml")`.
- **Reading specs**: ogen reads OpenAPI 3.0, 3.1 and 3.2 in YAML or JSON with its own parser. `$ref`s to other
  files and URLs are resolved relative to the file they stand in. Swagger 2.0 is not supported; convert such a spec to
  OpenAPI 3 first (e.g. with [swagger2openapi](https://github.com/Mermade/oas-kit/tree/main/packages/swagger2openapi)).
- **Operation IDs**: for operations without an `operationId` (an empty one counts as none),
  `operationIdGenerator { info -> ... }` derives one from an `EndpointInfo` (`tag`, `path`, `relativePath`,
  `method`). If it returns `null` or an empty name, or isn't set, the name is derived from the path and method.
- **Validation**: every spec is validated before code is generated; errors fail the build. The `validator` block sets
  naming conventions and rule severities, per spec or in `ogen {}` for all specs. See [Validator](docs/validator.md).

### Shared Models

To share the models between modules, e.g. a server and a client module, generate them in one module and let the
others depend on it. The other modules configure the same spec and package name with
`model { skipGeneration = true }`, so that their code refers to the shared models without generating them again.
Type and schema mappings and `beanValidation` must be the same in every module. Give each module its own
`utilPackageName`, as each generates its own utility code.

```kotlin
// api-models/build.gradle.kts
ogen {
    utilPackageName("com.example.api.gen.util")
    add(packageName = "com.example.api.gen") {
        specFile("$rootDir/specs/api.yaml")
        model {}
    }
}

// server/build.gradle.kts, with implementation(project(":api-models"))
ogen {
    utilPackageName("com.example.server.gen.util")
    add(packageName = "com.example.api.gen") {
        specFile("$rootDir/specs/api.yaml")
        model { skipGeneration = true }
        serverSpringV4 {}
    }
}
```

## Documentation

- [Models](docs/models.md): property names, objects and `additionalProperties`, Bean Validation
- [Spring Boot Server](docs/spring-server.md): security context, typed responses, request bodies, the QUERY method
- [Validator](docs/validator.md): naming conventions, all rules with their default severities, rule sets

## Tasks

The plugin registers the following tasks:

- `ogenGenerate`: Generates Kotlin code from the configured OpenAPI specifications into
  `build/generated/sources/ogen`, validating them first unless validation is turned off. This task is automatically
  hooked into the Kotlin compilation process. It runs on every build and is not compatible with the configuration
  cache.
- `ogenValidate`: Validates the OpenAPI specifications against the configured rules without generating code.

## Requirements

- JDK 21 or higher
- Kotlin 2.2 or higher

## License

This project is licensed under the [MIT License](LICENSE).
