package de.quati.ogen.plugin.intern

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import de.quati.kotlin.util.flatten
import de.quati.kotlin.util.takeIfNotEmpty
import de.quati.ogen.plugin.intern.model.config.InputConfig
import io.swagger.parser.OpenAPIParser
import io.swagger.v3.parser.core.models.ParseOptions
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.*


private val log = LoggerFactory.getLogger("SpecMerging")

internal fun InputConfig.Merge.buildMergedSpec(): Path {
    InputConfig.Format.entries.forEach { format -> mergeFilePath(format = format).deleteIfExists() }
    val specData = getAllSpecDataInDirectory()
    val format = specData.map { it.format }.singleOrNull() ?: InputConfig.Format.YAML
    val mergedFilePath = mergeFilePath(format = format)

    val mergedSpec = generateMergedSpec(specData)
    val mergedSpecString = format.objectMapper().writeValueAsString(mergedSpec)

    mergedFilePath.writeText(mergedSpecString)
    return mergedFilePath
}


private class SpecData(
    val version: String?,
    val specRelatedPath: String,
    val paths: List<String>,
) {
    val format get() = InputConfig.Format.parse(specRelatedPath)
}

private fun InputConfig.Format.objectMapper() = when (this) {
    InputConfig.Format.JSON -> ObjectMapper()
    InputConfig.Format.YAML -> ObjectMapper(YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER))
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


private fun InputConfig.Merge.readSpecData(path: Path) = try {
    log.info("Reading spec: $path")
    val result = OpenAPIParser().readLocation(
        path.toString(),
        null,
        ParseOptions().apply { isResolve = true },
    ).openAPI
    SpecData(
        version = result.openapi,
        specRelatedPath = directoryPath.relativize(path).toString(),
        paths = result.paths?.keys?.toList() ?: emptyList(),
    )
} catch (_: Exception) {
    log.warn("Failed to read file: $path. It would be ignored")
    null
}

private fun InputConfig.Merge.generateMergedSpec(
    specData: List<SpecData>,
): Map<String, *> {
    val baseSpec = when (baseConfig) {
        is InputConfig.Merge.BaseConfig.Data -> baseConfig.generateBaseSpec(specData)
        is InputConfig.Merge.BaseConfig.File -> baseConfig.generateBaseSpec()
    }
    val paths = specData.map { data ->
        data.paths.associateWith { path ->
            val specRelatedPath = "./${data.specRelatedPath}#/paths/" + path.replace("/", "~1")
            mapOf($$"$ref" to specRelatedPath)
        }
    }.flatten()
    return baseSpec + mapOf("paths" to paths)
}

private fun InputConfig.Merge.BaseConfig.Data.generateBaseSpec(specData: List<SpecData>): Map<String, *> = mapOf(
    "openapi" to (specData.mapNotNull { it.version }.maxOrNull() ?: "3.1.0"),
    "info" to buildMap {
        put("title", infoTitle)
        infoDescription?.also { put("description", it) }
        put("version", infoVersion)
    }
) + additional

@Suppress("UNCHECKED_CAST")
private fun InputConfig.Merge.BaseConfig.File.generateBaseSpec(): Map<String, *> =
    format.objectMapper().readValue(path.toFile(), Map::class.java) as Map<String, *>
