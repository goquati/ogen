package de.quati.ogen

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: BigDecimal) = encoder.encodeString(value.toPlainString())
    override fun deserialize(decoder: Decoder): BigDecimal = BigDecimal(decoder.decodeString())
}

object OffsetDateTimeSerializer : KSerializer<OffsetDateTime> {
    private val FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME!!
    override val descriptor = PrimitiveSerialDescriptor("OffsetDateTime", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: OffsetDateTime) = encoder.encodeString(FORMATTER.format(value))
    override fun deserialize(decoder: Decoder) = OffsetDateTime.parse(decoder.decodeString(), FORMATTER)
}