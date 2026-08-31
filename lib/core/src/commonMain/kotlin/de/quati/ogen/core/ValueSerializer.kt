package de.quati.ogen.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

public class ValueSerializer<V, T>(
    private val `inner`: KSerializer<T>,
    private val unwrap: (V) -> T,
    private val wrap: (T) -> V,
) : KSerializer<V> {
    override val descriptor: SerialDescriptor = inner.descriptor

    override fun serialize(encoder: Encoder, `value`: V) {
        encoder.encodeSerializableValue(inner, unwrap(value))
    }

    override fun deserialize(decoder: Decoder): V {
        val innerValue = decoder.decodeSerializableValue(inner)
        return wrap(innerValue)
    }
}
