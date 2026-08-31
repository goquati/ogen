package de.quati.ogen.core

import de.quati.kotlin.util.Option
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

public class OptionSerializer<T>(
    private val valueSerializer: KSerializer<T>,
) : KSerializer<Option<T>> {
    override val descriptor: SerialDescriptor = valueSerializer.descriptor

    override fun serialize(encoder: Encoder, `value`: Option<T>) {
        when (value) {
            is Option.Some<T> -> encoder.encodeSerializableValue(valueSerializer, value.value)
            Option.Undefined -> throw SerializationException("Option.Undefined must be omitted (use a default value + Json { encodeDefaults = false }).")
        }
    }

    override fun deserialize(decoder: Decoder): Option<T> {
        // If the field is absent, this serializer is not called at all -> default Option.Undefined is used.
        // If the field is present, we decode it as T (respecting T's nullability).
        return Option.Some(decoder.decodeSerializableValue(valueSerializer))
    }
}
