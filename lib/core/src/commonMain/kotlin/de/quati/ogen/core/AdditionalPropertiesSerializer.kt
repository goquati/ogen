package de.quati.ogen.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * The serializer of a generated class whose object allows additional properties: the keys the schema does not
 * declare go into a map of the class, and are written back from it. JSON only.
 *
 * @param inner the serializer the compiler plugin generated for the class, which skips the map
 * @param valueSerializer the serializer of the map's values; called on first use, so the values may be of the class
 *   itself or of classes that refer back to it
 * @param knownKeys the keys the schema declares, including a discriminator the class has no property for
 * @param get the map of additional properties of a value
 * @param set a copy of a value with the given additional properties
 */
public class AdditionalPropertiesSerializer<T, V>(
    private val inner: KSerializer<T>,
    valueSerializer: () -> KSerializer<V>,
    private val knownKeys: Set<String>,
    private val get: (T) -> Map<String, V>,
    private val set: (T, Map<String, V>) -> T,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = inner.descriptor
    private val innerKeys = (0 until descriptor.elementsCount).map(descriptor::getElementName).toSet()
    private val valueSerializer by lazy(valueSerializer)

    private fun JsonElement.asObject() = this as? JsonObject
        ?: throw SerializationException("${descriptor.serialName} must be a JSON object, but was $this")

    override fun serialize(encoder: Encoder, value: T) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("${descriptor.serialName} can only be serialized to JSON")
        val json = jsonEncoder.json
        val declared = json.encodeToJsonElement(inner, value).asObject()
        val additional = get(value)
        additional.keys.firstOrNull { it in knownKeys }?.let {
            throw SerializationException("'$it' is a property of ${descriptor.serialName}, not an additional one")
        }
        jsonEncoder.encodeJsonElement(
            JsonObject(declared + additional.mapValues { json.encodeToJsonElement(valueSerializer, it.value) })
        )
    }

    override fun deserialize(decoder: Decoder): T {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("${descriptor.serialName} can only be deserialized from JSON")
        val json = jsonDecoder.json
        val obj = jsonDecoder.decodeJsonElement().asObject()
        // A known key the class has no property for, the discriminator, is the business of the polymorphic serializer.
        val declared = json.decodeFromJsonElement(inner, JsonObject(obj.filterKeys { it in innerKeys }))
        val additional = obj.filterKeys { it !in knownKeys }
            .mapValues { json.decodeFromJsonElement(valueSerializer, it.value) }
        return set(declared, additional)
    }
}
