package de.quati.ogen.plugin.intern.oas.model

import de.quati.ogen.plugin.intern.oas.serial.OasSerialNames
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A Reference Object or the object itself. An object with a `$ref` key is a reference; its other keys
 * except `summary` and `description` are ignored, as the specification says.
 */
@Serializable(with = RefOrSerializer::class)
internal sealed interface RefOr<out T> {
    data class Ref(
        val ref: String,
        val summary: String? = null,
        val description: String? = null,
    ) : RefOr<Nothing>

    data class Value<out T>(val value: T) : RefOr<T>
}

internal class RefOrSerializer<T>(private val valueSerializer: KSerializer<T>) : KSerializer<RefOr<T>> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("${OasSerialNames.REF_OR}<${valueSerializer.descriptor.serialName}>") {
            element(OasSerialNames.REF_KEY, String.serializer().descriptor, isOptional = true)
            element("summary", String.serializer().descriptor, isOptional = true)
            element("description", String.serializer().descriptor, isOptional = true)
            element("value", valueSerializer.descriptor, isOptional = true)
        }

    override fun deserialize(decoder: Decoder): RefOr<T> {
        decoder as JsonDecoder
        val element = decoder.decodeJsonElement()
        if (element is JsonObject && OasSerialNames.REF_KEY in element) {
            fun string(key: String) = (element[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
            return RefOr.Ref(
                ref = string(OasSerialNames.REF_KEY) ?: throw SerializationException("'\$ref' must be a string"),
                summary = string("summary"),
                description = string("description"),
            )
        }
        return RefOr.Value(decoder.json.decodeFromJsonElement(valueSerializer, element))
    }

    override fun serialize(encoder: Encoder, value: RefOr<T>) {
        encoder as JsonEncoder
        val element: JsonElement = when (value) {
            is RefOr.Ref -> JsonObject(buildMap {
                put(OasSerialNames.REF_KEY, JsonPrimitive(value.ref))
                value.summary?.let { put("summary", JsonPrimitive(it)) }
                value.description?.let { put("description", JsonPrimitive(it)) }
            })
            is RefOr.Value -> encoder.json.encodeToJsonElement(valueSerializer, value.value)
        }
        encoder.encodeJsonElement(element)
    }
}
