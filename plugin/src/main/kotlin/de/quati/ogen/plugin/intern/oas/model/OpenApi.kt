package de.quati.ogen.plugin.intern.oas.model

import de.quati.ogen.plugin.intern.oas.serial.AnyValueSerializer
import de.quati.ogen.plugin.intern.oas.serial.Extensible
import de.quati.ogen.plugin.intern.oas.serial.ExtensibleMapSerializer
import de.quati.ogen.plugin.intern.oas.serial.ExtensibleSerializer
import de.quati.ogen.plugin.intern.oas.serial.OasSerialNames
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/*
 * The OpenAPI document as of 3.2. Fields that older versions lack are nullable like all optional fields;
 * a missing field is null, so "absent" and "empty" (e.g. `security: []`) stay apart.
 * Each class with extensions keeps its `x-*` keys in `extensions`.
 */

internal typealias SecurityRequirement = Map<String, List<String>>
internal typealias Content = Map<String, RefOr<MediaType>>

@Serializable(with = OpenApi.Serializer::class)
@KeepGeneratedSerializer
internal data class OpenApi(
    val openapi: String,
    @SerialName($$"$self") val self: String? = null,
    val info: Info,
    val jsonSchemaDialect: String? = null,
    val servers: List<Server>? = null,
    val paths: Paths? = null,
    val webhooks: Map<String, PathItem>? = null,
    val components: Components? = null,
    val security: List<SecurityRequirement>? = null,
    val tags: List<Tag>? = null,
    val externalDocs: ExternalDocs? = null,
    @SerialName(OasSerialNames.EXTENSIONS_KEY) override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extensible {
    internal object Serializer : ExtensibleSerializer<OpenApi>(generatedSerializer())
}

@Serializable(with = Info.Serializer::class)
@KeepGeneratedSerializer
internal data class Info(
    val title: String,
    val summary: String? = null,
    val description: String? = null,
    val termsOfService: String? = null,
    val contact: Contact? = null,
    val license: License? = null,
    val version: String,
    @SerialName(OasSerialNames.EXTENSIONS_KEY) override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extensible {
    internal object Serializer : ExtensibleSerializer<Info>(generatedSerializer())
}

@Serializable(with = Contact.Serializer::class)
@KeepGeneratedSerializer
internal data class Contact(
    val name: String? = null,
    val url: String? = null,
    val email: String? = null,
    @SerialName(OasSerialNames.EXTENSIONS_KEY) override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extensible {
    internal object Serializer : ExtensibleSerializer<Contact>(generatedSerializer())
}

@Serializable(with = License.Serializer::class)
@KeepGeneratedSerializer
internal data class License(
    val name: String,
    val identifier: String? = null,
    val url: String? = null,
    @SerialName(OasSerialNames.EXTENSIONS_KEY) override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extensible {
    internal object Serializer : ExtensibleSerializer<License>(generatedSerializer())
}

@Serializable(with = Server.Serializer::class)
@KeepGeneratedSerializer
internal data class Server(
    val url: String,
    val description: String? = null,
    val name: String? = null,
    val variables: Map<String, ServerVariable>? = null,
    @SerialName(OasSerialNames.EXTENSIONS_KEY) override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extensible {
    internal object Serializer : ExtensibleSerializer<Server>(generatedSerializer())
}

@Serializable(with = ServerVariable.Serializer::class)
@KeepGeneratedSerializer
internal data class ServerVariable(
    val enum: List<String>? = null,
    val default: String,
    val description: String? = null,
    @SerialName(OasSerialNames.EXTENSIONS_KEY) override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extensible {
    internal object Serializer : ExtensibleSerializer<ServerVariable>(generatedSerializer())
}

@Serializable(with = ExternalDocs.Serializer::class)
@KeepGeneratedSerializer
internal data class ExternalDocs(
    val description: String? = null,
    val url: String,
    @SerialName(OasSerialNames.EXTENSIONS_KEY) override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extensible {
    internal object Serializer : ExtensibleSerializer<ExternalDocs>(generatedSerializer())
}

@Serializable(with = Tag.Serializer::class)
@KeepGeneratedSerializer
internal data class Tag(
    val name: String,
    val summary: String? = null,
    val description: String? = null,
    val externalDocs: ExternalDocs? = null,
    val parent: String? = null,
    val kind: String? = null,
    @SerialName(OasSerialNames.EXTENSIONS_KEY) override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extensible {
    internal object Serializer : ExtensibleSerializer<Tag>(generatedSerializer())
}

@Serializable(with = Components.Serializer::class)
@KeepGeneratedSerializer
internal data class Components(
    val schemas: Map<String, Schema>? = null,
    val responses: Map<String, RefOr<Response>>? = null,
    val parameters: Map<String, RefOr<Parameter>>? = null,
    val examples: Map<String, RefOr<Example>>? = null,
    val requestBodies: Map<String, RefOr<RequestBody>>? = null,
    val headers: Map<String, RefOr<Header>>? = null,
    val securitySchemes: Map<String, RefOr<SecurityScheme>>? = null,
    val links: Map<String, RefOr<Link>>? = null,
    val callbacks: Map<String, RefOr<Callback>>? = null,
    val pathItems: Map<String, PathItem>? = null,
    val mediaTypes: Map<String, RefOr<MediaType>>? = null,
    @SerialName(OasSerialNames.EXTENSIONS_KEY) override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extensible {
    internal object Serializer : ExtensibleSerializer<Components>(generatedSerializer())
}

/** The paths, each starting with `/`, and extensions. */
@Serializable(with = Paths.Serializer::class)
internal data class Paths(
    val pathItems: Map<String, PathItem> = emptyMap(),
    override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extensible {
    internal object Serializer : ExtensibleMapSerializer<PathItem, Paths>(
        name = "Paths", valueSerializer = PathItem.serializer(), create = ::Paths, entries = { it.pathItems },
    )
}

/** A Path Item; it has a `$ref` field of its own, as its fields may stand next to the reference. */
@Serializable(with = PathItem.Serializer::class)
@KeepGeneratedSerializer
internal data class PathItem(
    @SerialName($$"$ref") val ref: String? = null,
    val summary: String? = null,
    val description: String? = null,
    val get: Operation? = null,
    val put: Operation? = null,
    val post: Operation? = null,
    val delete: Operation? = null,
    val options: Operation? = null,
    val head: Operation? = null,
    val patch: Operation? = null,
    val trace: Operation? = null,
    val query: Operation? = null,
    val additionalOperations: Map<String, Operation>? = null,
    val servers: List<Server>? = null,
    val parameters: List<RefOr<Parameter>>? = null,
    @SerialName(OasSerialNames.EXTENSIONS_KEY) override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extensible {
    internal object Serializer : ExtensibleSerializer<PathItem>(generatedSerializer())
}

@Serializable(with = Operation.Serializer::class)
@KeepGeneratedSerializer
internal data class Operation(
    val tags: List<String>? = null,
    val summary: String? = null,
    val description: String? = null,
    val externalDocs: ExternalDocs? = null,
    val operationId: String? = null,
    val parameters: List<RefOr<Parameter>>? = null,
    val requestBody: RefOr<RequestBody>? = null,
    val responses: Responses? = null,
    val callbacks: Map<String, RefOr<Callback>>? = null,
    val deprecated: Boolean? = null,
    val security: List<SecurityRequirement>? = null,
    val servers: List<Server>? = null,
    @SerialName(OasSerialNames.EXTENSIONS_KEY) override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extensible {
    internal object Serializer : ExtensibleSerializer<Operation>(generatedSerializer())
}

@Serializable(with = Parameter.Serializer::class)
@KeepGeneratedSerializer
internal data class Parameter(
    val name: String,
    @SerialName("in") val location: Location,
    val description: String? = null,
    val required: Boolean? = null,
    val deprecated: Boolean? = null,
    val allowEmptyValue: Boolean? = null,
    val style: String? = null,
    val explode: Boolean? = null,
    val allowReserved: Boolean? = null,
    val schema: Schema? = null,
    @Serializable(with = AnyValueSerializer::class) val example: JsonElement? = null,
    val examples: Map<String, RefOr<Example>>? = null,
    val content: Content? = null,
    @SerialName(OasSerialNames.EXTENSIONS_KEY) override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extensible {
    @Serializable
    enum class Location {
        @SerialName("query") QUERY,
        @SerialName("querystring") QUERYSTRING,
        @SerialName("header") HEADER,
        @SerialName("path") PATH,
        @SerialName("cookie") COOKIE,
    }

    internal object Serializer : ExtensibleSerializer<Parameter>(generatedSerializer())
}

@Serializable(with = RequestBody.Serializer::class)
@KeepGeneratedSerializer
internal data class RequestBody(
    val description: String? = null,
    val content: Content,
    val required: Boolean? = null,
    @SerialName(OasSerialNames.EXTENSIONS_KEY) override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extensible {
    internal object Serializer : ExtensibleSerializer<RequestBody>(generatedSerializer())
}

@Serializable(with = MediaType.Serializer::class)
@KeepGeneratedSerializer
internal data class MediaType(
    val description: String? = null,
    val schema: Schema? = null,
    val itemSchema: Schema? = null,
    @Serializable(with = AnyValueSerializer::class) val example: JsonElement? = null,
    val examples: Map<String, RefOr<Example>>? = null,
    val encoding: Map<String, Encoding>? = null,
    val prefixEncoding: List<Encoding>? = null,
    val itemEncoding: Encoding? = null,
    @SerialName(OasSerialNames.EXTENSIONS_KEY) override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extensible {
    internal object Serializer : ExtensibleSerializer<MediaType>(generatedSerializer())
}

@Serializable(with = Encoding.Serializer::class)
@KeepGeneratedSerializer
internal data class Encoding(
    val contentType: String? = null,
    val headers: Map<String, RefOr<Header>>? = null,
    val style: String? = null,
    val explode: Boolean? = null,
    val allowReserved: Boolean? = null,
    val encoding: Map<String, Encoding>? = null,
    val prefixEncoding: List<Encoding>? = null,
    val itemEncoding: Encoding? = null,
    @SerialName(OasSerialNames.EXTENSIONS_KEY) override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extensible {
    internal object Serializer : ExtensibleSerializer<Encoding>(generatedSerializer())
}

/** The responses by status code (`200`, `4XX`) or `default`, and extensions. */
@Serializable(with = Responses.Serializer::class)
internal data class Responses(
    val responses: Map<String, RefOr<Response>> = emptyMap(),
    override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extensible {
    internal object Serializer : ExtensibleMapSerializer<RefOr<Response>, Responses>(
        name = "Responses",
        valueSerializer = RefOr.serializer(Response.serializer()),
        create = ::Responses,
        entries = { it.responses },
    )
}

@Serializable(with = Response.Serializer::class)
@KeepGeneratedSerializer
internal data class Response(
    val summary: String? = null,
    val description: String? = null,
    val headers: Map<String, RefOr<Header>>? = null,
    val content: Content? = null,
    val links: Map<String, RefOr<Link>>? = null,
    @SerialName(OasSerialNames.EXTENSIONS_KEY) override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extensible {
    internal object Serializer : ExtensibleSerializer<Response>(generatedSerializer())
}

/** The path items by runtime expression, and extensions. */
@Serializable(with = Callback.Serializer::class)
internal data class Callback(
    val pathItems: Map<String, PathItem> = emptyMap(),
    override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extensible {
    internal object Serializer : ExtensibleMapSerializer<PathItem, Callback>(
        name = "Callback", valueSerializer = PathItem.serializer(), create = ::Callback, entries = { it.pathItems },
    )
}

@Serializable(with = Example.Serializer::class)
@KeepGeneratedSerializer
internal data class Example(
    val summary: String? = null,
    val description: String? = null,
    @Serializable(with = AnyValueSerializer::class) val value: JsonElement? = null,
    val externalValue: String? = null,
    @Serializable(with = AnyValueSerializer::class) val dataValue: JsonElement? = null,
    val serializedValue: String? = null,
    @SerialName(OasSerialNames.EXTENSIONS_KEY) override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extensible {
    internal object Serializer : ExtensibleSerializer<Example>(generatedSerializer())
}

@Serializable(with = Link.Serializer::class)
@KeepGeneratedSerializer
internal data class Link(
    val operationRef: String? = null,
    val operationId: String? = null,
    val parameters: Map<String, JsonElement>? = null,
    @Serializable(with = AnyValueSerializer::class) val requestBody: JsonElement? = null,
    val description: String? = null,
    val server: Server? = null,
    @SerialName(OasSerialNames.EXTENSIONS_KEY) override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extensible {
    internal object Serializer : ExtensibleSerializer<Link>(generatedSerializer())
}

@Serializable(with = Header.Serializer::class)
@KeepGeneratedSerializer
internal data class Header(
    val description: String? = null,
    val required: Boolean? = null,
    val deprecated: Boolean? = null,
    val allowEmptyValue: Boolean? = null,
    val style: String? = null,
    val explode: Boolean? = null,
    val allowReserved: Boolean? = null,
    val schema: Schema? = null,
    @Serializable(with = AnyValueSerializer::class) val example: JsonElement? = null,
    val examples: Map<String, RefOr<Example>>? = null,
    val content: Content? = null,
    @SerialName(OasSerialNames.EXTENSIONS_KEY) override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extensible {
    internal object Serializer : ExtensibleSerializer<Header>(generatedSerializer())
}

@Serializable(with = SecurityScheme.Serializer::class)
@KeepGeneratedSerializer
internal data class SecurityScheme(
    val type: Type,
    val description: String? = null,
    val name: String? = null,
    @SerialName("in") val location: Location? = null,
    val scheme: String? = null,
    val bearerFormat: String? = null,
    val flows: OAuthFlows? = null,
    val openIdConnectUrl: String? = null,
    val oauth2MetadataUrl: String? = null,
    val deprecated: Boolean? = null,
    @SerialName(OasSerialNames.EXTENSIONS_KEY) override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extensible {
    @Serializable
    enum class Type {
        @SerialName("apiKey") API_KEY,
        @SerialName("http") HTTP,
        @SerialName("mutualTLS") MUTUAL_TLS,
        @SerialName("oauth2") OAUTH2,
        @SerialName("openIdConnect") OPEN_ID_CONNECT,
    }

    @Serializable
    enum class Location {
        @SerialName("query") QUERY,
        @SerialName("header") HEADER,
        @SerialName("cookie") COOKIE,
    }

    internal object Serializer : ExtensibleSerializer<SecurityScheme>(generatedSerializer())
}

@Serializable(with = OAuthFlows.Serializer::class)
@KeepGeneratedSerializer
internal data class OAuthFlows(
    val implicit: OAuthFlow? = null,
    val password: OAuthFlow? = null,
    val clientCredentials: OAuthFlow? = null,
    val authorizationCode: OAuthFlow? = null,
    val deviceAuthorization: OAuthFlow? = null,
    @SerialName(OasSerialNames.EXTENSIONS_KEY) override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extensible {
    internal object Serializer : ExtensibleSerializer<OAuthFlows>(generatedSerializer())
}

/** One OAuth flow; which URLs it requires depends on the flow. */
@Serializable(with = OAuthFlow.Serializer::class)
@KeepGeneratedSerializer
internal data class OAuthFlow(
    val authorizationUrl: String? = null,
    val deviceAuthorizationUrl: String? = null,
    val tokenUrl: String? = null,
    val refreshUrl: String? = null,
    val scopes: Map<String, String>,
    @SerialName(OasSerialNames.EXTENSIONS_KEY) override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extensible {
    internal object Serializer : ExtensibleSerializer<OAuthFlow>(generatedSerializer())
}
