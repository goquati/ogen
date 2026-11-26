import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import de.quati.ogen.plugin.intern.oas.read.SourceReader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.math.BigDecimal
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Reads every spec of the repository with the new reader and with Jackson, which swagger-parser uses today.
 * Both trees must be equal; numbers are compared by value. Goes away with Jackson (plan, phase 8).
 */
class TestSourceReaderCompat {
    private val jackson = ObjectMapper(YAMLFactory())

    // YAML 1.1 (Jackson) reads unquoted yes/no/on/off/y/n as booleans; YAML 1.2 reads them as strings.
    // These specs test exactly that; the enum check's YAML 1.1 hints change with phase 7.
    private val yaml11Booleans = setOf(
        "enum-values-boolean-cast.yaml",
        "enum-values-yaml-booleans.yaml",
        "enum-values-yaml-booleans-3.1.yaml",
    )
    private val yaml11BooleanWords = Regex("y|yes|n|no|on|off", RegexOption.IGNORE_CASE)

    @OptIn(ExperimentalPathApi::class)
    private fun specFiles(): List<Path> = listOf(Path("src/test/resources"), Path("../tests"))
        .flatMap { root -> root.walk().filter { it.isRegularFile() && it.extension in setOf("yaml", "yml", "json") } }
        .filterNot { "/build/" in it.toString() || "/node_modules/" in it.toString() || "kotlin-js-store" in it.toString() }

    @Test
    fun `new reader reads every spec like jackson`() {
        val files = specFiles()
        assertTrue(files.size > 50, "found only ${files.size} files")
        val yaml11Differences = mutableSetOf<String>()
        val problems = files.mapNotNull { path ->
            val result = SourceReader.read(path)
            val element = result.element ?: return@mapNotNull "$path: ${result.diagnostics}"
            val expected = Json.parseToJsonElement(ObjectMapper().writeValueAsString(jackson.readTree(path.toFile())))
            diff(expected, element, "") { yaml11Differences += path.name }?.let { "$path: $it" }
        }
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
        assertEquals(yaml11Booleans, yaml11Differences)
    }

    private fun diff(expected: JsonElement, actual: JsonElement, at: String, yaml11Boolean: () -> Unit): String? = when {
        expected is JsonObject && actual is JsonObject ->
            if (expected.keys != actual.keys) "$at: keys ${expected.keys} != ${actual.keys}"
            else expected.keys.firstNotNullOfOrNull { diff(expected.getValue(it), actual.getValue(it), "$at/$it", yaml11Boolean) }
        expected is JsonArray && actual is JsonArray ->
            if (expected.size != actual.size) "$at: size ${expected.size} != ${actual.size}"
            else expected.indices.firstNotNullOfOrNull { diff(expected[it], actual[it], "$at/$it", yaml11Boolean) }
        expected is JsonPrimitive && actual is JsonPrimitive -> when {
            expected == actual -> null
            !expected.isString && !actual.isString && expected.booleanOrNull == null &&
                expected.content.toBigDecimalOrNull()?.let { e -> actual.content.toBigDecimalOrNull()?.compareTo(e) == 0 } == true -> null
            !expected.isString && expected.booleanOrNull != null && actual.isString && yaml11BooleanWords.matches(actual.content) ->
                null.also { yaml11Boolean() }
            else -> "$at: $expected != $actual"
        }
        else -> "$at: $expected != $actual"
    }

    private fun String.toBigDecimalOrNull() = runCatching { BigDecimal(this) }.getOrNull()
}
