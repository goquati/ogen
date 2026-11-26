package de.quati.ogen.plugin.intern.oas.decode

import de.quati.ogen.plugin.intern.oas.Diagnostic
import de.quati.ogen.plugin.intern.oas.SourceLocation
import de.quati.ogen.plugin.intern.oas.model.OpenApi
import de.quati.ogen.plugin.intern.oas.read.ReadResult
import de.quati.ogen.plugin.intern.oas.ref.JsonPointer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** The decoded value, or null if [diagnostics] has an error. Warnings come with a value. */
internal class DecodeResult<T>(val value: T?, val diagnostics: List<Diagnostic>)

/**
 * Decodes JSON trees into the OAS model: first [StructureCheck] collects all problems with their location;
 * only a tree without errors is decoded.
 */
internal object OasDecoder {
    val json: Json = Json {
        // A number or boolean where a string is expected, e.g. `version: 1.0`, is read as its text.
        isLenient = true
        // The structure check has reported unknown fields; the ones left are ignored on purpose ($ref siblings).
        ignoreUnknownKeys = true
    }

    fun decode(read: ReadResult): DecodeResult<OpenApi> {
        val element = read.element ?: return DecodeResult(null, read.diagnostics)
        return decode(element, OpenApi.serializer(), JsonPointer.ROOT, read::locationOf)
    }

    fun <T> decode(
        element: JsonElement,
        serializer: KSerializer<T>,
        pointer: JsonPointer,
        locate: (pointer: JsonPointer, key: Boolean) -> SourceLocation,
    ): DecodeResult<T> {
        val diagnostics = StructureCheck(locate).check(element, serializer.descriptor, pointer)
        if (diagnostics.any { it.severity == Diagnostic.Severity.ERROR }) return DecodeResult(null, diagnostics)
        return try {
            DecodeResult(json.decodeFromJsonElement(serializer, element), diagnostics)
        } catch (e: SerializationException) {
            // The structure check should have found it; report it anyway instead of failing.
            DecodeResult(null, diagnostics + Diagnostic.error("cannot decode: ${e.message}", locate(pointer, false)))
        }
    }

    fun <T> encode(value: T, serializer: KSerializer<T>): JsonElement = json.encodeToJsonElement(serializer, value)
}
