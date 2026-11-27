package de.quati.ogen.plugin.intern.oas.ref

import de.quati.ogen.plugin.intern.oas.Diagnostic
import de.quati.ogen.plugin.intern.oas.OasVersion
import de.quati.ogen.plugin.intern.oas.SourceLocation
import de.quati.ogen.plugin.intern.oas.decode.DecodeResult
import de.quati.ogen.plugin.intern.oas.decode.OasDecoder
import de.quati.ogen.plugin.intern.oas.load.LoadedSpec
import de.quati.ogen.plugin.intern.oas.load.Located
import de.quati.ogen.plugin.intern.oas.model.RefOr
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement

/**
 * Resolves the absolute references of a [LoadedSpec] when they are needed, decoding the target as the
 * expected type. Results are cached; nothing is dereferenced ahead of time, so recursive schemas are fine.
 */
internal class Resolver(private val spec: LoadedSpec) {
    private val cache = mutableMapOf<Pair<String, String>, DecodeResult<*>>()

    fun locate(ref: String): Located? = runCatching { RefTarget.parse(ref, spec.rootUri) }.getOrNull()?.let(spec::locate)

    fun element(ref: String): JsonElement? = locate(ref)?.let { spec.documents[it.document]?.element?.at(it.pointer) }

    /** The value [ref] points to, decoded with [serializer]; its diagnostics name the target's file and position. */
    @Suppress("UNCHECKED_CAST")
    fun <T> resolve(ref: String, serializer: KSerializer<T>): DecodeResult<T> =
        cache.getOrPut(ref to serializer.descriptor.serialName) {
            val located = locate(ref)
            val document = located?.let { spec.documents[it.document] }
            val element = document?.element?.at(located.pointer)
            if (element == null)
                DecodeResult<T>(null, listOf(Diagnostic.error("cannot resolve '$ref'", SourceLocation(ref))))
            else
                OasDecoder.decode(element, serializer, located.pointer, document::locationOf, spec.sourceVersion ?: OasVersion.LATEST)
        } as DecodeResult<T>

    /**
     * The object behind [value]: the value itself, or what its reference points to, following references to
     * references. A cycle of references is an error.
     */
    fun <T> resolve(value: RefOr<T>, serializer: KSerializer<T>): DecodeResult<T> {
        val seen = mutableListOf<String>()
        var current: RefOr<T> = value
        while (true) {
            when (current) {
                is RefOr.Value -> return DecodeResult(current.value, emptyList())
                is RefOr.Ref -> {
                    if (current.ref in seen) return DecodeResult(
                        null,
                        listOf(Diagnostic.error("references form a cycle: ${(seen + current.ref).joinToString(" -> ")}", SourceLocation(current.ref))),
                    )
                    seen += current.ref
                    val next = resolve(current.ref, RefOr.serializer(serializer))
                    current = next.value ?: return DecodeResult(null, next.diagnostics)
                }
            }
        }
    }
}
