package de.quati.ogen.plugin.intern.oas.model

import de.quati.ogen.plugin.intern.oas.serial.AnyValueSerializer
import de.quati.ogen.plugin.intern.oas.serial.BigDecimalSerializer
import de.quati.ogen.plugin.intern.oas.serial.Extensible
import de.quati.ogen.plugin.intern.oas.serial.ExtensibleSerializer
import de.quati.ogen.plugin.intern.oas.serial.OasRequired
import de.quati.ogen.plugin.intern.oas.serial.OasSerialNames
import de.quati.ogen.plugin.intern.oas.serial.StringOrListSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.math.BigDecimal

/**
 * A Schema Object: `true`, `false` or an object.
 *
 * [Obj] holds the common keywords as fields; all other keywords stay in [Obj.other] and `x-*` keys in
 * [Obj.extensions]. `$ref` is a field of its own, as since 3.1 other keywords may stand next to it.
 */
@Serializable(with = SchemaSerializer::class)
internal sealed interface Schema {
    data class Bool(val value: Boolean) : Schema

    @Serializable
    data class Obj(
        @SerialName($$"$ref") val ref: String? = null,
        @SerialName($$"$id") val id: String? = null,
        @SerialName($$"$schema") val schema: String? = null,
        @SerialName($$"$defs") val defs: Map<String, Schema>? = null,
        val title: String? = null,
        val description: String? = null,
        @Serializable(with = StringOrListSerializer::class) val type: List<String>? = null,
        val format: String? = null,
        val enum: List<JsonElement>? = null,
        @Serializable(with = AnyValueSerializer::class) val const: JsonElement? = null,
        @Serializable(with = AnyValueSerializer::class) val default: JsonElement? = null,
        val examples: List<JsonElement>? = null,
        @Serializable(with = AnyValueSerializer::class) val example: JsonElement? = null,
        @Serializable(with = BigDecimalSerializer::class) val multipleOf: BigDecimal? = null,
        @Serializable(with = BigDecimalSerializer::class) val maximum: BigDecimal? = null,
        @Serializable(with = BigDecimalSerializer::class) val exclusiveMaximum: BigDecimal? = null,
        @Serializable(with = BigDecimalSerializer::class) val minimum: BigDecimal? = null,
        @Serializable(with = BigDecimalSerializer::class) val exclusiveMinimum: BigDecimal? = null,
        val maxLength: Int? = null,
        val minLength: Int? = null,
        val pattern: String? = null,
        val items: Schema? = null,
        val prefixItems: List<Schema>? = null,
        val maxItems: Int? = null,
        val minItems: Int? = null,
        val uniqueItems: Boolean? = null,
        val properties: Map<String, Schema>? = null,
        val patternProperties: Map<String, Schema>? = null,
        val additionalProperties: Schema? = null,
        val required: List<String>? = null,
        val maxProperties: Int? = null,
        val minProperties: Int? = null,
        val allOf: List<Schema>? = null,
        val anyOf: List<Schema>? = null,
        val oneOf: List<Schema>? = null,
        val not: Schema? = null,
        @SerialName("if") val ifSchema: Schema? = null,
        @SerialName("then") val thenSchema: Schema? = null,
        @SerialName("else") val elseSchema: Schema? = null,
        val dependentSchemas: Map<String, Schema>? = null,
        val dependentRequired: Map<String, List<String>>? = null,
        val propertyNames: Schema? = null,
        val contains: Schema? = null,
        val minContains: Int? = null,
        val maxContains: Int? = null,
        val unevaluatedItems: Schema? = null,
        val unevaluatedProperties: Schema? = null,
        val readOnly: Boolean? = null,
        val writeOnly: Boolean? = null,
        val deprecated: Boolean? = null,
        val contentMediaType: String? = null,
        val contentEncoding: String? = null,
        val discriminator: Discriminator? = null,
        val xml: Xml? = null,
        val externalDocs: ExternalDocs? = null,
        @SerialName(OasSerialNames.EXTENSIONS_KEY) override val extensions: Map<String, JsonElement> = emptyMap(),
        /** Keywords without a field of their own, e.g. `$anchor`, `$comment` or, before migration, `nullable`. */
        @SerialName(OasSerialNames.OTHER_KEY) val other: Map<String, JsonElement> = emptyMap(),
    ) : Schema, Extensible
}

internal object SchemaSerializer : KSerializer<Schema> {
    private val objSerializer get() = Schema.Obj.serializer()

    override val descriptor: SerialDescriptor by lazy {
        buildClassSerialDescriptor(OasSerialNames.SCHEMA) {
            element("object", objSerializer.descriptor, isOptional = true)
        }
    }

    private val knownKeys: Set<String> by lazy {
        objSerializer.descriptor.elementNames.toSet() - OasSerialNames.EXTENSIONS_KEY - OasSerialNames.OTHER_KEY
    }

    override fun deserialize(decoder: Decoder): Schema {
        decoder as JsonDecoder
        return when (val element = decoder.decodeJsonElement()) {
            is JsonPrimitive -> Schema.Bool(
                element.takeUnless { it.isString }?.booleanOrNull
                    ?: throw SerializationException("a schema must be an object or a boolean")
            )
            is JsonObject -> {
                val known = mutableMapOf<String, JsonElement>()
                val extensions = mutableMapOf<String, JsonElement>()
                val other = mutableMapOf<String, JsonElement>()
                element.forEach { (key, value) ->
                    when {
                        key in knownKeys -> known[key] = value
                        OasSerialNames.isExtension(key) -> extensions[key] = value
                        else -> other[key] = value
                    }
                }
                if (extensions.isNotEmpty()) known[OasSerialNames.EXTENSIONS_KEY] = JsonObject(extensions)
                if (other.isNotEmpty()) known[OasSerialNames.OTHER_KEY] = JsonObject(other)
                decoder.json.decodeFromJsonElement(objSerializer, JsonObject(known))
            }
            else -> throw SerializationException("a schema must be an object or a boolean")
        }
    }

    override fun serialize(encoder: Encoder, value: Schema) {
        encoder as JsonEncoder
        val element = when (value) {
            is Schema.Bool -> JsonPrimitive(value.value)
            is Schema.Obj -> {
                val fields = encoder.json.encodeToJsonElement(objSerializer, value) as JsonObject
                val extensions = fields[OasSerialNames.EXTENSIONS_KEY] as? JsonObject ?: JsonObject(emptyMap())
                val other = fields[OasSerialNames.OTHER_KEY] as? JsonObject ?: JsonObject(emptyMap())
                JsonObject(fields - OasSerialNames.EXTENSIONS_KEY - OasSerialNames.OTHER_KEY + other + extensions)
            }
        }
        encoder.encodeJsonElement(element)
    }
}

@Serializable(with = Discriminator.Serializer::class)
@KeepGeneratedSerializer
internal data class Discriminator(
    @OasRequired val propertyName: String? = null,
    val mapping: Map<String, String>? = null,
    val defaultMapping: String? = null,
    @SerialName(OasSerialNames.EXTENSIONS_KEY) override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extensible {
    internal object Serializer : ExtensibleSerializer<Discriminator>(generatedSerializer())
}

@Serializable(with = Xml.Serializer::class)
@KeepGeneratedSerializer
internal data class Xml(
    val name: String? = null,
    val namespace: String? = null,
    val prefix: String? = null,
    val attribute: Boolean? = null,
    val wrapped: Boolean? = null,
    val nodeType: String? = null,
    @SerialName(OasSerialNames.EXTENSIONS_KEY) override val extensions: Map<String, JsonElement> = emptyMap(),
) : Extensible {
    internal object Serializer : ExtensibleSerializer<Xml>(generatedSerializer())
}
