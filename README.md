# ogen - OpenAPI Generator for Kotlin

`ogen` is a Gradle plugin that generates Kotlin code from OpenAPI specifications. It is designed to be idiomatic, supporting both Kotlin Multiplatform (KMP) and modern server/client frameworks.

## Features

- **Type-safe Models**: Generates Kotlin data classes and enums with [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) support.
- **Shared Models**: Core feature allowing models to be generated in a separate Gradle module, shared between server and client.
- **Advanced OpenAPI Support**: Supports OpenAPI 3.0, 3.1 and 3.2, including `oneOf` (via sealed interfaces), `allOf`, `anyOf`, and complex nested schemas.
- **Kotlin Multiplatform**: Seamlessly works with KMP projects, generating code into the appropriate source sets.
- **Spring Boot Support**: Generates server interfaces for Spring Boot (WebFlux).
- **Ktor Client Support**: Generates Ktor clients (a Ktor server generator is planned).
- **Bean Validation**: Optionally generates Jakarta Bean Validation annotations from the schemas' validation
  keywords, for models (also in multiplatform code) and the Spring server.
- **Multipart Requests**: Supports `multipart/form-data` request bodies, including file uploads, in the Spring server and the Ktor client.
- **OpenAPI Validation**: Built-in validator to ensure your OpenAPI specs follow best practices and naming conventions.
- **Highly Configurable**: Custom type mappings, schema mappings, and naming convention enforcement.

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
            // Naming conventions (warnings by default, see rule namingConventions)
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
        // (a JVM target needs jakarta.validation-api, the server spring-boot-starter-validation)
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
            // Adds an OperationContext parameter (containing meta-info about the endpoint) to each generated function
            addOperationContext = true
            // Optional: If the operation has any security requirements, add the specified class as a parameter
            contextIfAnySecurity("com.example.api.AuthContext")
            // Optional: Return typed ApiResponse classes instead of ResponseEntity (requires de.quati.ogen:server-spring)
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
  files and URLs are resolved relative to the file they stand in. The validator reports problems in a spec with
  file and line. Swagger 2.0 is not supported; convert such a spec to OpenAPI 3 first (e.g. with
  [swagger2openapi](https://github.com/Mermade/oas-kit/tree/main/packages/swagger2openapi)).
- **Operation IDs**: for operations without an `operationId` (an empty one counts as none),
  `operationIdGenerator { info -> ... }` derives one from an `EndpointInfo` (`tag`, `path`, `relativePath`,
  `method`). If it returns `null` or an empty name, or isn't set, the name is derived from the path and method.
- **Validator settings**: besides `rules` (below), the `validator` block sets naming conventions in `naming {}`:
  `schema` (default `PascalCase`), `property`, `tag` and `operationId` (default `CamelCase`), `pathSegment` and
  `pathExtension` for the names and extensions in paths (both default `KebabCase`), `enumValue` for the values of
  string enums (default `Any`) and, via `parameter { path; query; header; cookie }`, parameter names (default
  `CamelCase` for path and query, `Any` for header and cookie). A path segment splits at `:`; in each piece, leading
  dots are dropped, the text up to the next `.` is a name (`pathSegment`) and each part after it an extension
  (`pathExtension`): `{id}.json:download` checks the name `download` and the extension `json`, and
  `.well-known` the name `well-known`. A part matches if it does as written, or with every variable replaced by `xx`,
  or by `Xx`, since the variable's value is unknown: `by{field}Name` in camelCase, `{Id}Report` in PascalCase, but also
  `report{year}` in kebab-case. A part that is only variables is not checked. `except(...)` on these two names one
  part (`Legacy_API`), not a whole segment, and turning off the checks on paths takes both set to `Any`.
  How a violation is reported is the severity of the rule `namingConventions` in `rules {}`.
  Besides the predefined conventions, `NameConvention.RegexFormat("...")` and `NameConvention.Custom { name -> ... }`
  define custom ones. `CamelCase`, `PascalCase`, `KebabCase` and `RegexFormat` take a block: `except(...)` lists
  exact names the convention accepts as they are (e.g. a third-party header), and on `CamelCase` and `PascalCase`,
  `acronyms` says whether a run of capitals counts as one word (`true`, the default: `UUID`, `HTTPError`, `apiID`)
  or every word is one capital followed by lower-case letters and digits, with a single capital allowed only as the
  last word, as in `PointX` (`false`).

  ```kotlin
  naming {
      schema = NameConvention.PascalCase { acronyms = false }
      parameter {
          header = NameConvention.RegexFormat("^[A-Z][A-Za-z0-9]*(?:-[A-Z][A-Za-z0-9]*)*$") {
              except("X-HubSpot-Signature-v3")
          }
      }
  }
  ```

  The conventions apply to every schema and parameter the generator sees, inline request and
  response schemas, composition members and path-level parameters included.
- **Validation by default**: every spec is validated before code is generated, also without a `validator` block,
  and the parser's errors and the rules that are errors by default fail the build. `validator { enabled = false }`
  turns validation off for one spec, or in `ogen {}` for every spec that doesn't turn it on again.
- **Settings for every spec**: a `validator` block in `ogen {}` sets the validator settings of every spec, also those
  without a `validator` block of their own. A spec's `validator` block starts from these settings and changes only
  what it sets, also inside `naming {}`, `parameter {}` and `rules {}`; without a `validator` block in `ogen {}`, it starts
  from the defaults.

  ```kotlin
  ogen {
      validator {                                             // applies to every spec below
          naming { tag = NameConvention.PascalCase }
          rules { ambiguousPaths = Severity.Error }
      }
      add(packageName = "com.example.portal") {
          specFile("$rootDir/spec/portal.yaml")               // validated with the settings above
          model {}
      }
      add(packageName = "com.example.thirdparty") {
          specFile("$projectDir/oas/thirdparty.yaml")
          validator { naming { tag = NameConvention.Any } }   // overrides only what it sets
          model {}
      }
      add(packageName = "com.example.legacy") {
          specFile("$projectDir/oas/legacy.yaml")
          validator { enabled = false }                       // not validated
          model {}
      }
  }
  ```
- **Rules**: `rules {}` in a `validator` block sets each rule's severity: `Severity.Error` fails the build,
  `Severity.Warning` is only reported, `Severity.Off` doesn't run the check. There is one property per rule
  (`namingConventions`, `enumValues`, `unusedSchemas`, `ambiguousPaths`, `getWithBody`, …), or
  `this[ValidatorRule.X] = …`. Each defaults to what the list below says. `rules(set) { … }` first sets the
  severities of a rule set, a `Map<ValidatorRule, Severity>`, then those of the block:
  `RuleSet.Default` (the defaults), `RuleSet.Strict` (every rule an error, also those that are off by default such
  as `operationIdRequired`, so any finding fails the build) and
  `RuleSet.Lenient` (allows as much as possible, e.g. for specs of third parties: what makes a spec invalid stays an
  error, other problems stay warnings, recommendations, naming conventions among them, are off). Without a set, every rule
  has its default, as with `RuleSet.Default`; in a spec's `validator` block, `rules(RuleSet.Default)` sets the defaults
  again for that spec, over what `ogen {}` sets. The parser's errors always fail the build. A component schema that no operation uses on purpose, such as an enum the code uses directly, is
  kept from the `unusedSchemas` warning by `x-ogen-keep: true` (or `"true"`) on the schema; the schemas it refers to
  count as used, too. On a spec with parser errors, the checks that are errors by default and `unusedSchemas` don't
  run, whatever their severity.
- **What the validator checks**: the parser's errors fail the build, and by default these checks: broken
  path templates (an empty `{}`, an unclosed `{` or a stray `}`), a query string or fragment in a path key, path
  parameters missing from the path template, paths that differ only in their variables' names, security schemes not
  defined in `components.securitySchemes`, a `type` that isn't a JSON Schema type, enum values that don't match their
  schema's type or are listed twice, a `default` its `enum` doesn't list, `required` names without a matching property,
  contradictory constraints such as `minimum` above `maximum`, and compositions no value can match: `allOf` members
  whose types exclude each other or the schema's own type, or a type that excludes every `oneOf` or `anyOf` member.
  These are warnings: paths with a trailing slash, operations without a 2xx or 3xx response, operation tags missing from
  the top-level `tags` (when it declares any), `nullable` without a `type` in OpenAPI 3.0, schemas no operation uses,
  `pattern` regexes and
  `patternProperties` keys that don't compile as Java regexes (OpenAPI's are JavaScript's, which differ slightly),
  keywords that apply to none of the schema's declared types, such as `maxLength` on an integer, in OpenAPI 3.1
  `maximum` next to `exclusiveMaximum` or `minimum` next to `exclusiveMinimum`, where only the tighter bound has an
  effect, and ambiguous paths: two paths that share a method where neither is more concrete (`/{entity}/me` and
  `/books/{id}`). Recommendations are warnings too, and `RuleSet.Lenient` turns them off: `properties`
  next to `oneOf`, a request body on GET or HEAD, header names with `_`, `nullable` in OpenAPI 3.1 and later (which
  has no effect there), schema keywords the spec's version deprecates (`example` since 3.1, the XML Object's
  `attribute` and `wrapped` since 3.2), keywords next to a `$ref` that are ignored, `exclusiveMinimum: true` without
  `minimum` in 3.0, operations ogen generates no code for (3.2's `additionalOperations`), and names that
  don't match the naming conventions. Off by default: `operationIdRequired`, operations under `paths` without an
  `operationId`, for projects that choose every generated method name in the spec instead of letting ogen derive it.
  Specs that are only generated as models, often with no operation at all, get an `unusedSchemas` warning for every
  schema; such projects set `rules { unusedSchemas = Severity.Off }`.

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

### Bean Validation

`beanValidation = true` in a spec's `add {}` block annotates the model properties with the Jakarta Bean Validation
constraints of their schemas, and the Spring controller parameters too; request bodies get `@Valid`. Spring then
answers invalid requests with `400 Bad Request`. Generators without validation, such as `clientKtor`, ignore it. If the
models are generated in another module (`model { skipGeneration = true }`), set `beanValidation` on the spec in both
modules.

The annotations are those of Jakarta (`jakarta.validation:jakarta.validation-api`). Models in multiplatform common
code use those of `de.quati.ogen.core.validation` instead: on the JVM they are the Jakarta annotations, which then
needs `jakarta.validation-api` at compile time; on other platforms the compiler drops them. `de.quati.ogen:core`
registers a value extractor, so constraints on optional (`Option`) properties apply to their values.

They check `minLength`, `maxLength`, `pattern` and `format: email` of strings, `minimum`, `maximum`,
`exclusiveMinimum`, `exclusiveMaximum` and `multipleOf` of integers and numbers (also those mapped to `BigDecimal` or
`BigInteger`), `minItems`, `maxItems` and `uniqueItems` of arrays and `minProperties` and `maxProperties` of maps, and
cascade into generated objects. Jakarta has no constraints for `multipleOf` and `uniqueItems`, so these are
`@MultipleOf` and `@UniqueItems` of `de.quati.ogen.core.validation`, on every platform.

Constraints of the items of arrays and the values of maps go on the type arguments, e.g. `List<@Size(max = 3) String>`;
Hibernate Validator reads them from JVM type annotations, so with `beanValidation` the plugin adds
`-Xemit-jvm-type-annotations` to the Kotlin JVM compilations.

### Objects and `additionalProperties`

A property's Kotlin name is its name in camelCase, with `@SerialName` keeping the JSON name when they differ. When
several names have the same camelCase form, e.g. `xTenant` and `x-tenant`, the name that already is that form keeps
it and the others get `_` appended until their name is free (`xTenant`, `xTenant_`).

What a `type: object` schema without `allOf`, `anyOf` or `oneOf` becomes, the same in every OpenAPI version and
wherever the schema is used (with a composition, any `additionalProperties` makes it `JsonElement`, and so does an
`allOf` or `anyOf` of classes that allow other keys, also through `patternProperties`):

| Schema | Kotlin type |
|---|---|
| `properties`, without `additionalProperties` and `patternProperties`, or with `additionalProperties: false` and no `patternProperties` | a class |
| `properties` and `additionalProperties: true` or a schema, or `patternProperties` | a class with a map of the other keys |
| no `properties`, `additionalProperties: true` or `{}` | `Map<String, JsonElement>` |
| no `properties`, `additionalProperties` with a schema, or `patternProperties` | `Map<String, T>` |
| no `properties`, `additionalProperties: false` and no `patternProperties` | `JsonElement` |
| no `properties`, no `additionalProperties` and no `patternProperties` | `JsonElement` |

A class that allows other keys keeps them in its last property, `additionalProperties: Map<String, T>` (with a `_`
appended while a declared property has that name). Its serializer reads every key the schema doesn't declare into
the map and writes the map's entries next to the declared properties; it works with `Json` only.

```yaml
Thing:
  type: object
  properties: { id: { type: string } }
  additionalProperties: { type: integer }
```

```kotlin
val thing = ThingDto(id = "a".toOption(), additionalProperties = mapOf("count" to 3))
// {"id":"a","count":3}
```

- `T` is the type of the `additionalProperties` schema, `JsonElement` for `true`.
- `patternProperties` share the map with `additionalProperties`, in a class as in a plain map: when all their schemas
  (and that of `additionalProperties`, unless it is `false`) are the same, `T` is its type, otherwise `JsonElement`.
  The keys are not matched against the patterns. Without `additionalProperties`, keys that match no pattern are
  allowed too, so `T` is `JsonElement`. A pattern whose schema is `false` allows no key and counts for nothing.
- Without `additionalProperties` and `patternProperties`, the class has no map: unknown keys are ignored or rejected
  as the `Json` configuration says (`ignoreUnknownKeys`).
- A key of the map that the schema declares as a property fails serialization.
- A class discriminator that `Json` writes into the object (in a sealed interface, or in every object with
  `ClassDiscriminatorMode.ALL_JSON_OBJECTS`) is no additional property.
- A `required` name that `properties` doesn't declare becomes a property of its own when the object allows it, with
  the schema that allows it: that of the matching pattern (`JsonElement` if several with different schemas match;
  patterns are matched as Java regexes, and one that doesn't compile as such may match, which makes it `JsonElement`),
  otherwise that of `additionalProperties`. Without `additionalProperties`, it is `JsonElement` if there are patterns
  (other than `false`), and the name is left as it is (to an `allOf` sibling, say) if there are none. Such a property
  is required and checked like any declared one, and it makes a class also of an object without `properties`:

  ```yaml
  Thing:
    type: object
    required: [tenant]
    properties: { id: { type: string } }
    additionalProperties: { type: string }
  ```

  ```kotlin
  ThingDto(id: Option<String>, tenant: String, additionalProperties: Map<String, String>)
  ```

  With `additionalProperties: false`, a required name that no pattern allows is a spec error, and so is one that a
  pattern with `false` matches.
- With `beanValidation`, `minProperties` and `maxProperties` of a class with a map are checked as the map's size
  when every property is required, so that the number of keys is known: a class that only has required names from
  `required` keeps the bounds of the map it was. With an optional property they are not checked, nor in a variant of a
  sealed interface, whose class discriminator is a key only when the value is (de)serialized as the interface.

### The QUERY method

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
