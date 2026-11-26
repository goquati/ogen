package de.quati.ogen.plugin.intern.oas.serial

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.JsonUnquotedLiteral
import java.math.BigDecimal

/**
 * Names of the descriptors the structure check treats specially, and the keys under which extensions and
 * unknown schema keywords travel through the generated serializers.
 */
internal object OasSerialNames {
    const val ANY_VALUE = "de.quati.oas.AnyValue"
    const val BIG_DECIMAL = "de.quati.oas.BigDecimal"
    const val STRING_OR_LIST = "de.quati.oas.StringOrList"
    const val REF_OR = "de.quati.oas.RefOr"
    const val SCHEMA = "de.quati.oas.Schema"
    const val EXTENSIBLE_MAP = "de.quati.oas.ExtensibleMap"

    const val EXTENSIONS_KEY = $$"$extensions"
    const val OTHER_KEY = $$"$other"
    const val REF_KEY = $$"$ref"

    fun isExtension(key: String) = key.startsWith("x-")
}

/**
 * A field the specification requires. It is nullable in the model, so a spec without it is still read (as
 * swagger-parser read it, and code is generated from it); the structure check reports it as missing.
 */
@kotlinx.serialization.SerialInfo
@Target(AnnotationTarget.PROPERTY)
internal annotation class OasRequired

/** An object that may carry specification extensions (`x-*`). */
internal interface Extensible {
    val extensions: Map<String, JsonElement>
}

private val Decoder.json get() = (this as? JsonDecoder ?: throw SerializationException("only JSON is supported")).json
private val Encoder.json get() = (this as? JsonEncoder ?: throw SerializationException("only JSON is supported")).json
private fun Decoder.element() = (this as JsonDecoder).decodeJsonElement()
private fun Encoder.element(element: JsonElement) = (this as JsonEncoder).encodeJsonElement(element)

/**
 * Wraps the generated serializer of an [Extensible] class: on the way in, all `x-*` keys move into one object
 * under [OasSerialNames.EXTENSIONS_KEY], which the class declares as its `extensions` property; on the way out,
 * they move back.
 */
internal abstract class ExtensibleSerializer<T : Extensible>(generated: KSerializer<T>) :
    JsonTransformingSerializer<T>(generated) {
    override fun transformDeserialize(element: JsonElement): JsonElement = splitExtensions(element)
    override fun transformSerialize(element: JsonElement): JsonElement = joinExtensions(element)
}

internal fun splitExtensions(element: JsonElement): JsonElement {
    if (element !is JsonObject) return element
    val (extensions, rest) = element.entries.partition { OasSerialNames.isExtension(it.key) }
    if (extensions.isEmpty()) return element
    return JsonObject(rest.associate { it.toPair() } +
        (OasSerialNames.EXTENSIONS_KEY to JsonObject(extensions.associate { it.toPair() })))
}

internal fun joinExtensions(element: JsonElement): JsonElement {
    if (element !is JsonObject) return element
    val extensions = element[OasSerialNames.EXTENSIONS_KEY] as? JsonObject ?: return element
    return JsonObject(element - OasSerialNames.EXTENSIONS_KEY + extensions)
}

/**
 * Any JSON value, including an explicit `null`, which stays [JsonNull]; a missing value is Kotlin `null`.
 * Used for `default`, `const`, `example` and the like, where `null` is a value of its own.
 */
internal object AnyValueSerializer : KSerializer<JsonElement?> {
    override val descriptor: SerialDescriptor =
        SerialDescriptor(OasSerialNames.ANY_VALUE, JsonElement.serializer().descriptor).nullable

    override fun deserialize(decoder: Decoder): JsonElement = decoder.element()
    override fun serialize(encoder: Encoder, value: JsonElement?) = encoder.element(value ?: JsonNull)
}

/** A JSON number of any size and precision. */
internal object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(OasSerialNames.BIG_DECIMAL, PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): BigDecimal {
        val primitive = decoder.element() as? JsonPrimitive
        if (primitive == null || primitive.isString || primitive is JsonNull)
            throw SerializationException("expected a number")
        return primitive.content.toBigDecimalOrNull() ?: throw SerializationException("expected a number")
    }

    override fun serialize(encoder: Encoder, value: BigDecimal) = encoder.element(JsonUnquotedLiteral(value.toString()))
}

/** A string or an array of strings, such as a schema's `type`; one string is written without array. */
internal object StringOrListSerializer : KSerializer<List<String>> {
    private val list = ListSerializer(String.serializer())
    override val descriptor: SerialDescriptor = SerialDescriptor(OasSerialNames.STRING_OR_LIST, list.descriptor)

    override fun deserialize(decoder: Decoder): List<String> = when (val element = decoder.element()) {
        is JsonArray -> decoder.json.decodeFromJsonElement(list, element)
        is JsonPrimitive -> listOf(element.content)
        else -> throw SerializationException("expected a string or an array of strings")
    }

    override fun serialize(encoder: Encoder, value: List<String>) =
        encoder.element(value.singleOrNull()?.let(::JsonPrimitive) ?: encoder.json.encodeToJsonElement(list, value))
}

/**
 * An object that maps names to values and may carry extensions, such as Paths, Responses and Callback.
 * Its descriptor has the value's descriptor as element `value`; [name] tells the structure check which one it is.
 */
internal open class ExtensibleMapSerializer<V, T : Extensible>(
    name: String,
    private val valueSerializer: KSerializer<V>,
    private val create: (Map<String, V>, Map<String, JsonElement>) -> T,
    private val entries: (T) -> Map<String, V>,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("${OasSerialNames.EXTENSIBLE_MAP}<$name>") {
        element("value", valueSerializer.descriptor, isOptional = true)
    }

    override fun deserialize(decoder: Decoder): T {
        val element = decoder.element() as? JsonObject ?: throw SerializationException("expected an object")
        val (extensions, values) = element.entries.partition { OasSerialNames.isExtension(it.key) }
        return create(
            values.associate { (key, value) -> key to decoder.json.decodeFromJsonElement(valueSerializer, value) },
            extensions.associate { it.toPair() },
        )
    }

    override fun serialize(encoder: Encoder, value: T) = encoder.element(
        JsonObject(entries(value).mapValues { encoder.json.encodeToJsonElement(valueSerializer, it.value) } + value.extensions)
    )
}
