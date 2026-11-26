package de.quati.ogen.plugin.intern

import de.quati.kotlin.util.flatten
import de.quati.kotlin.util.takeIfNotEmpty
import de.quati.ogen.plugin.intern.model.config.InputConfig
import de.quati.ogen.plugin.intern.oas.read.SourceReader
import de.quati.ogen.plugin.intern.oas.ref.JsonPointer
import de.quati.ogen.plugin.intern.oas.write.JsonTreeWriter
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import org.slf4j.LoggerFactory
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.file.Path
import kotlin.io.path.*


private val log = LoggerFactory.getLogger("SpecMerging")

internal fun InputConfig.Merge.buildMergedSpec(): Path {
    InputConfig.Format.entries.forEach { format -> mergeFilePath(format = format).deleteIfExists() }
    val specData = getAllSpecDataInDirectory()
    val format = specData.map { it.format }.distinct().singleOrNull() ?: InputConfig.Format.YAML
    val mergedFilePath = mergeFilePath(format = format)

    val mergedSpec = generateMergedSpec(specData)
    val mergedSpecString = when (format) {
        InputConfig.Format.JSON -> JsonTreeWriter.toJson(mergedSpec)
        InputConfig.Format.YAML -> JsonTreeWriter.toYaml(mergedSpec)
    }

    mergedFilePath.writeText(mergedSpecString)
    return mergedFilePath
}


private class SpecData(
    val version: String,
    val specRelatedPath: String,
    val paths: List<String>,
) {
    val format get() = InputConfig.Format.parse(specRelatedPath)
}

private fun InputConfig.Merge.getAllSpecDataInDirectory(): List<SpecData> = try {
    val ignorePath = when (baseConfig) {
        is InputConfig.Merge.BaseConfig.Data -> null
        is InputConfig.Merge.BaseConfig.File -> baseConfig.path.absolute()
    }
    @OptIn(ExperimentalPathApi::class)
    directoryPath.walk()
        .filter { it.isRegularFile() && it.absolute() != ignorePath }
        .mapNotNull { readSpecData(it) }
        .toList()
} catch (e: IOException) {
    throw RuntimeException("Exception while listing files in spec root directory: $directoryPath", e)
}.takeIfNotEmpty() ?: throw RuntimeException("Spec directory '$directoryPath' doesn't contain valid specification")


/**
 * The version and the path names of one file, read without resolving references.
 * A file that cannot be read or has no `openapi` field is not a spec and is left out.
 */
private fun InputConfig.Merge.readSpecData(path: Path): SpecData? {
    log.info("Reading spec: $path")
    val result = SourceReader.read(path)
    val root = result.element as? JsonObject
    val version = (root?.get("openapi") as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
    if (root == null || version == null) {
        val reason = result.diagnostics.firstOrNull()?.message ?: "it has no 'openapi' field"
        log.warn("Failed to read file: $path ($reason). It would be ignored")
        return null
    }
    return SpecData(
        version = version,
        specRelatedPath = directoryPath.relativize(path).toString(),
        paths = (root["paths"] as? JsonObject)?.keys?.toList() ?: emptyList(),
    )
}

private fun InputConfig.Merge.generateMergedSpec(
    specData: List<SpecData>,
): JsonObject {
    val baseSpec = when (baseConfig) {
        is InputConfig.Merge.BaseConfig.Data -> baseConfig.generateBaseSpec(specData)
        is InputConfig.Merge.BaseConfig.File -> baseConfig.generateBaseSpec()
    }
    val paths = specData.map { data ->
        data.paths.associateWith { path ->
            val pointer = JsonPointer.ROOT / "paths" / path
            JsonObject(mapOf($$"$ref" to JsonPrimitive("./${data.specRelatedPath}#$pointer")))
        }
    }.flatten()
    return JsonObject(baseSpec + mapOf("paths" to JsonObject(paths)))
}

private fun InputConfig.Merge.BaseConfig.Data.generateBaseSpec(specData: List<SpecData>): Map<String, JsonElement> =
    mapOf(
        "openapi" to JsonPrimitive(maxOasVersion(specData.map { it.version }) ?: "3.1.0"),
        "info" to JsonObject(buildMap {
            put("title", JsonPrimitive(infoTitle))
            infoDescription?.also { put("description", JsonPrimitive(it)) }
            put("version", JsonPrimitive(infoVersion))
        }),
    ) + additional.mapValues { (key, value) -> value.toJsonElement(key) }

private fun InputConfig.Merge.BaseConfig.File.generateBaseSpec(): JsonObject {
    val result = SourceReader.read(path)
    return result.element as? JsonObject ?: throw RuntimeException(
        "Base spec '$path' is not a valid specification: " +
            (result.diagnostics.joinToString("; ").ifEmpty { "it is not an object" })
    )
}

/** The highest version, compared part by part as numbers (`3.10.0` > `3.2.0`). */
internal fun maxOasVersion(versions: List<String>): String? = versions.maxWithOrNull(
    compareBy<String, List<Int>>(lexicographic()) { v -> v.split('.').map { it.toIntOrNull() ?: -1 } }
        .thenBy { it }
)

private fun lexicographic() = Comparator<List<Int>> { a, b ->
    a.zip(b).firstOrNull { (x, y) -> x != y }?.let { (x, y) -> x.compareTo(y) } ?: a.size.compareTo(b.size)
}

/** A value of the Gradle DSL (`mergedFileAdditional`) as JSON; [path] names it in errors. */
@OptIn(ExperimentalSerializationApi::class)
internal fun Any?.toJsonElement(path: String): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is CharSequence -> JsonPrimitive(toString())
    is Boolean -> JsonPrimitive(this)
    is Byte, is Short, is Int, is Long, is BigInteger -> JsonUnquotedLiteral(toString())
    is Float, is Double -> if ((this as Number).toDouble().isFinite())
        JsonUnquotedLiteral(toString())
    else
        throw IllegalArgumentException("Value '$this' at '$path' has no JSON representation")
    is BigDecimal -> JsonUnquotedLiteral(toString())
    is Enum<*> -> JsonPrimitive(name)
    is Map<*, *> -> JsonObject(entries.associate { (k, v) -> k.toString() to v.toJsonElement("$path.$k") })
    is Iterable<*> -> JsonArray(mapIndexed { i, v -> v.toJsonElement("$path[$i]") })
    is Array<*> -> JsonArray(mapIndexed { i, v -> v.toJsonElement("$path[$i]") })
    else -> throw IllegalArgumentException("Value at '$path' has the unsupported type ${this::class.qualifiedName}")
}
