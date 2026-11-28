package de.quati.ogen.plugin.intern.oas.read

import de.quati.ogen.plugin.intern.oas.Diagnostic
import de.quati.ogen.plugin.intern.oas.SourceLocation
import de.quati.ogen.plugin.intern.oas.SourcePosition
import de.quati.ogen.plugin.intern.oas.ref.JsonPointer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.api.lowlevel.Compose
import org.snakeyaml.engine.v2.exceptions.Mark
import org.snakeyaml.engine.v2.exceptions.MarkedYamlEngineException
import org.snakeyaml.engine.v2.exceptions.YamlEngineException
import org.snakeyaml.engine.v2.nodes.MappingNode
import org.snakeyaml.engine.v2.nodes.Node
import org.snakeyaml.engine.v2.nodes.ScalarNode
import org.snakeyaml.engine.v2.nodes.SequenceNode
import org.snakeyaml.engine.v2.nodes.Tag
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Reads YAML, and with it JSON, straight into a JSON tree, keeping the position of every value. Scalars resolve as
 * swagger-parser resolved them ([Yaml11Schema]); like swagger-parser, which read them through Jackson, floats are
 * doubles, while integers keep their exact value. Duplicate keys are errors, as in swagger-parser; non-string keys
 * become strings.
 */
internal object YamlReader {
    // Large specs exceed snakeyaml-engine's default limit of 3 MB.
    private const val CODE_POINT_LIMIT = 64 * 1024 * 1024
    // swagger-parser allowed any number of aliases (`maxYamlAliasesForCollections`).
    private const val MAX_ALIASES_FOR_COLLECTIONS = Int.MAX_VALUE

    fun read(text: String, source: String, format: ReadResult.Format = ReadResult.Format.YAML): ReadResult {
        val settings = LoadSettings.builder()
            .setLabel(source)
            .setSchema(Yaml11Schema)
            .setUseMarks(true)
            .setCodePointLimit(CODE_POINT_LIMIT)
            .setMaxAliasesForCollections(MAX_ALIASES_FOR_COLLECTIONS)
            .build()
        fun failed(message: String, mark: Mark?) = ReadResult.failed(
            source = source,
            format = format,
            diagnostic = Diagnostic.error(message, SourceLocation(source = source, position = mark?.toPosition())),
        )

        val documents = try {
            Compose(settings).composeAllFromString(text).toList()
        } catch (e: MarkedYamlEngineException) {
            val message = listOfNotNull(e.context, e.problem).joinToString(separator = ", ")
            return failed("invalid ${format.name}: $message", (e.problemMark.orElse(null) ?: e.contextMark.orElse(null)))
        } catch (e: YamlEngineException) {
            return failed("invalid ${format.name}: ${e.message}", null)
        }
        val root = when (documents.size) {
            0 -> return failed("the file is empty", null)
            1 -> documents.single()
            else -> return failed(
                "the file contains ${documents.size} YAML documents; only one is supported",
                documents[1].startMark.orElse(null),
            )
        }
        val converter = NodeConverter(source)
        val element = converter.convert(root, JsonPointer.ROOT)
        return ReadResult(
            source = source,
            format = format,
            element = element.takeIf { converter.diagnostics.isEmpty() },
            positions = converter.positions,
            diagnostics = converter.diagnostics,
            keyPositions = converter.keyPositions,
            yaml11Scalars = converter.yaml11Scalars,
        )
    }

    private fun Mark.toPosition() = SourcePosition(line = line + 1, column = column + 1)
    private val Node.position get() = startMark.orElse(null)?.toPosition()

    private class NodeConverter(private val source: String) {
        val positions = LinkedHashMap<JsonPointer, SourcePosition>()
        val keyPositions = LinkedHashMap<JsonPointer, SourcePosition>()
        val yaml11Scalars = LinkedHashMap<JsonPointer, String>()
        val diagnostics = mutableListOf<Diagnostic>()
        private val inProgress: MutableSet<Node> = Collections.newSetFromMap(IdentityHashMap())

        private fun error(message: String, pointer: JsonPointer, node: Node) {
            diagnostics += Diagnostic.error(message, SourceLocation(source, pointer, node.position))
        }

        fun convert(node: Node, pointer: JsonPointer): JsonElement {
            node.position?.let { positions[pointer] = it }
            return when (node) {
                is ScalarNode -> scalar(node, pointer)
                is SequenceNode -> nested(node, pointer) {
                    JsonArray(node.value.mapIndexed { index, item -> convert(item, pointer / index) })
                }
                is MappingNode -> nested(node, pointer) { mapping(node, pointer) }
                else -> JsonNull.also { error("unsupported YAML node '${node.nodeType}'", pointer, node) }
            }
        }

        // An alias may point back into the collection it is part of; such a value has no JSON form.
        private fun nested(node: Node, pointer: JsonPointer, block: () -> JsonElement): JsonElement {
            if (!inProgress.add(node)) {
                error("recursive YAML alias", pointer, node)
                return JsonNull
            }
            try {
                return block()
            } finally {
                inProgress.remove(node)
            }
        }

        private fun mapping(node: MappingNode, pointer: JsonPointer): JsonObject {
            val keyNodes = HashMap<String, Node>()
            val content = LinkedHashMap<String, JsonElement>()
            for (tuple in node.value) {
                val keyNode = tuple.keyNode
                if (keyNode !is ScalarNode) {
                    error("a ${keyNode.nodeType.name.lowercase()} cannot be a key", pointer, keyNode)
                    continue
                }
                val key = keyNode.value
                val first = keyNodes.putIfAbsent(key, keyNode)
                if (first != null) {
                    val firstAt = first.position?.let { " (first defined at $it)" } ?: ""
                    error("duplicate key '$key'$firstAt", pointer / key, keyNode)
                    continue
                }
                keyNode.position?.let { keyPositions[pointer / key] = it }
                content[key] = convert(tuple.valueNode, pointer / key)
            }
            return JsonObject(content)
        }

        private fun scalar(node: ScalarNode, pointer: JsonPointer): JsonElement {
            if (node.isPlain && node.tag in YAML11_TAGS && !Yaml11Schema.isYaml12NonString(node.value))
                yaml11Scalars[pointer] = node.value
            return scalarValue(node, pointer)
        }

        private fun scalarValue(node: ScalarNode, pointer: JsonPointer): JsonElement = when (node.tag) {
            Tag.NULL -> JsonNull
            Tag.BOOL -> JsonPrimitive(Yaml11Schema.toBoolean(node.value))
            Tag.INT -> number(Yaml11Schema.toInteger(node.value).toString())
            // A double, as swagger-parser read it: `0.010` is 0.01.
            Tag.FLOAT -> Yaml11Schema.toDouble(node.value)?.let { number(it.toString()) } ?: JsonNull.also {
                error("'${node.value}' has no JSON representation", pointer, node)
            }
            // Strings, and scalars with any other tag, keep their text.
            else -> JsonPrimitive(node.value)
        }

        private val YAML11_TAGS = setOf(Tag.NULL, Tag.BOOL, Tag.INT, Tag.FLOAT)

        @OptIn(ExperimentalSerializationApi::class)
        private fun number(literal: String) = JsonUnquotedLiteral(literal)
    }
}
