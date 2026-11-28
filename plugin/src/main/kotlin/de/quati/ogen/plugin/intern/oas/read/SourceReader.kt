package de.quati.ogen.plugin.intern.oas.read

import de.quati.ogen.plugin.intern.oas.Diagnostic
import de.quati.ogen.plugin.intern.oas.SourceLocation
import de.quati.ogen.plugin.intern.oas.SourcePosition
import de.quati.ogen.plugin.intern.oas.ref.JsonPointer
import kotlinx.serialization.json.JsonElement
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * The content of one source file as a JSON tree.
 * [element] is null when the file could not be read; [diagnostics] then names the reason.
 */
internal class ReadResult(
    val source: String,
    val format: Format?,
    val element: JsonElement?,
    val positions: Map<JsonPointer, SourcePosition>,
    val diagnostics: List<Diagnostic>,
    /** Where the key of a mapping entry stands; [positions] has where its value starts. */
    val keyPositions: Map<JsonPointer, SourcePosition> = emptyMap(),
    /**
     * Plain scalars that YAML 1.1, as the reader resolves them, reads as a boolean, number or null, but YAML 1.2 as a
     * string, such as `no`, `on` or `12:30`; by their place, with the text as written.
     */
    val yaml11Scalars: Map<JsonPointer, String> = emptyMap(),
) {
    enum class Format { JSON, YAML }

    /** The location of the value at [pointer], or with [key] of the key it stands under. */
    fun locationOf(pointer: JsonPointer, key: Boolean = false): SourceLocation = SourceLocation(
        source = source,
        pointer = pointer,
        position = (if (key) keyPositions[pointer] else null) ?: positions[pointer],
    )

    companion object {
        fun failed(source: String, format: Format?, diagnostic: Diagnostic) = ReadResult(
            source = source,
            format = format,
            element = null,
            positions = emptyMap(),
            diagnostics = listOf(diagnostic),
        )
    }
}

internal object SourceReader {
    fun read(path: Path): ReadResult {
        val text = try {
            path.readText()
        } catch (e: IOException) {
            val source = path.toString()
            return ReadResult.failed(
                source = source,
                format = null,
                diagnostic = Diagnostic.error("cannot read file: ${e.message ?: e::class.simpleName}", SourceLocation(source)),
            )
        }
        return read(text = text, source = path.toString())
    }

    /**
     * Text whose first non-whitespace character is `{` is JSON; everything else is YAML.
     * Both go through the YAML reader, as YAML 1.2 is a superset of JSON: unlike `Json.parseToJsonElement`,
     * which keeps the last of two equal keys, it reports duplicate keys, and it knows the positions.
     */
    fun read(text: String, source: String): ReadResult {
        val content = text.removePrefix(BYTE_ORDER_MARK)
        val format = if (content.trimStart().startsWith("{")) ReadResult.Format.JSON else ReadResult.Format.YAML
        return YamlReader.read(content, source, format)
    }

    private const val BYTE_ORDER_MARK = "\uFEFF"
}
