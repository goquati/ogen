package de.quati.ogen.plugin.intern.oas.write

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.snakeyaml.engine.v2.api.DumpSettings
import org.snakeyaml.engine.v2.api.StreamDataWriter
import org.snakeyaml.engine.v2.common.FlowStyle
import org.snakeyaml.engine.v2.common.ScalarStyle
import org.snakeyaml.engine.v2.emitter.Emitter
import org.snakeyaml.engine.v2.events.DocumentEndEvent
import org.snakeyaml.engine.v2.events.DocumentStartEvent
import org.snakeyaml.engine.v2.events.Event
import org.snakeyaml.engine.v2.events.ImplicitTuple
import org.snakeyaml.engine.v2.events.MappingEndEvent
import org.snakeyaml.engine.v2.events.MappingStartEvent
import org.snakeyaml.engine.v2.events.ScalarEvent
import org.snakeyaml.engine.v2.events.SequenceEndEvent
import org.snakeyaml.engine.v2.events.SequenceStartEvent
import org.snakeyaml.engine.v2.events.StreamEndEvent
import org.snakeyaml.engine.v2.events.StreamStartEvent
import java.util.Optional

/**
 * Writes a JSON tree as compact JSON or as block-style YAML.
 *
 * The YAML has the layout Jackson's YAMLGenerator (with its default features) gave the merged spec before, so
 * existing merge files keep their content: string values in double quotes, keys plain unless YAML 1.1 would
 * read them as another type, no `---`, an indent of 2 without extra indent for sequences, lines split at 80.
 */
internal object JsonTreeWriter {
    private val json = Json

    fun toJson(element: JsonElement): String = json.encodeToString(JsonElement.serializer(), element)

    fun toYaml(element: JsonElement): String {
        val settings = DumpSettings.builder()
            .setDefaultFlowStyle(FlowStyle.BLOCK)
            .setExplicitStart(false)
            .setCanonical(false)
            .setIndent(2)
            .setIndicatorIndent(0)
            .setWidth(80)
            .setSplitLines(true)
            .setUseUnicodeEncoding(true)
            .setMaxSimpleKeyLength(128)
            .setBestLineBreak("\n")
            .build()
        val output = StringBuilderWriter()
        val emitter = Emitter(settings, output)
        emitter.emit(StreamStartEvent())
        emitter.emit(DocumentStartEvent(false, Optional.empty(), emptyMap()))
        emitter.emitElement(element)
        emitter.emit(DocumentEndEvent(false))
        emitter.emit(StreamEndEvent())
        return output.toString()
    }

    private fun Emitter.emitElement(element: JsonElement) {
        when (element) {
            is JsonObject -> {
                emit(MappingStartEvent(Optional.empty(), Optional.empty(), true, FlowStyle.BLOCK))
                element.forEach { (key, value) ->
                    emitScalar(key, if (needsQuotedKey(key)) ScalarStyle.DOUBLE_QUOTED else ScalarStyle.PLAIN)
                    emitElement(value)
                }
                emit(MappingEndEvent())
            }
            is JsonArray -> {
                emit(SequenceStartEvent(Optional.empty(), Optional.empty(), true, FlowStyle.BLOCK))
                element.forEach { emitElement(it) }
                emit(SequenceEndEvent())
            }
            JsonNull -> emitScalar("null", ScalarStyle.PLAIN)
            is JsonPrimitive -> emitScalar(
                element.content,
                if (element.isString) ScalarStyle.DOUBLE_QUOTED else ScalarStyle.PLAIN,
            )
        }
    }

    private fun Emitter.emitScalar(value: String, style: ScalarStyle) = emit(
        ScalarEvent(Optional.empty(), Optional.empty(), ImplicitTuple(true, true), value, style) as Event
    )

    // YAML 1.1 keywords, names that look like numbers, and control characters; as Jackson decides it.
    private val reservedKeywords = setOf(
        "false", "False", "FALSE", "n", "N", "no", "No", "NO", "null", "Null", "NULL", "on", "On", "ON",
        "off", "Off", "OFF", "true", "True", "TRUE", "y", "Y", "yes", "Yes", "YES",
    )

    private fun needsQuotedKey(key: String) = key.isEmpty() ||
        key in reservedKeywords ||
        key[0] == '~' ||
        key[0] in "0123456789-+." ||
        key.any { it < ' ' }

    private class StringBuilderWriter : StreamDataWriter {
        private val builder = StringBuilder()
        override fun write(str: String) {
            builder.append(str)
        }

        override fun write(str: String, off: Int, len: Int) {
            builder.append(str, off, off + len)
        }

        override fun toString(): String = builder.toString()
    }
}
