package de.quati.ogen.plugin.intern.oas.decode

import de.quati.ogen.plugin.intern.oas.Diagnostic
import de.quati.ogen.plugin.intern.oas.OasVersion
import de.quati.ogen.plugin.intern.oas.SourceLocation
import de.quati.ogen.plugin.intern.oas.model.OpenApi
import de.quati.ogen.plugin.intern.oas.read.ReadResult
import de.quati.ogen.plugin.intern.oas.ref.JsonPointer
import de.quati.ogen.plugin.intern.oas.ref.remove
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * The decoded value, and what is wrong with it. A value comes even with errors, as long as the parts that fit the
 * model can be decoded; it is null only if not even those can be.
 */
internal class DecodeResult<T>(val value: T?, val diagnostics: List<Diagnostic>)

/**
 * Decodes JSON trees into the OAS model: first [StructureCheck] collects all problems with their location. Then the
 * tree is decoded without the values that do not fit, as swagger-parser read a spec: a value of the wrong type is
 * left out, an unknown field ignored, a missing required field is null. The version is the one the spec was written
 * in, which decides which fields are known ([StructureCheck]).
 */
internal object OasDecoder {
    val json: Json = Json {
        // A number or boolean where a string is expected, e.g. `version: 1.0`, is read as its text.
        isLenient = true
        // The structure check has reported unknown fields; the ones left are ignored on purpose ($ref siblings).
        ignoreUnknownKeys = true
        // An enum value the model does not know is null, as swagger-parser left such a field empty.
        coerceInputValues = true
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
        version: OasVersion = OasVersion.LATEST,
    ): DecodeResult<T> {
        val check = StructureCheck(version, locate)
        val diagnostics = check.check(element, serializer.descriptor, pointer)
        val fitting = element.remove(check.invalidValues.mapNotNull { it.relativeTo(pointer) }.toSet())
        return try {
            DecodeResult(json.decodeFromJsonElement(serializer, fitting), diagnostics)
        } catch (e: SerializationException) {
            // The structure check should have found it; report it anyway instead of failing.
            DecodeResult(null, diagnostics + Diagnostic.error("cannot decode: ${e.message}", locate(pointer, false)))
        }
    }

    fun <T> encode(value: T, serializer: KSerializer<T>): JsonElement = json.encodeToJsonElement(serializer, value)
}
