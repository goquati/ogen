import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.walk

/** Every YAML and JSON file of the plugin's test resources and of the tests/ projects, except build output. */
@OptIn(ExperimentalPathApi::class)
internal fun repositorySpecFiles(): List<Path> = listOf(Path("src/test/resources"), Path("../tests"))
    .flatMap { root -> root.walk().filter { it.isRegularFile() && it.extension in setOf("yaml", "yml", "json") } }
    .filterNot { path ->
        val p = path.toString()
        "/build/" in p || "/node_modules/" in p || "kotlin-js-store" in p || "/oas-schemas/" in p
    }
    .sorted()

/**
 * The first difference between two JSON trees, or null. Numbers compare by value; [tolerate] may accept
 * other differences (it gets the expected and actual value, either may be null for a missing key).
 */
internal fun jsonDiff(
    expected: JsonElement?,
    actual: JsonElement?,
    at: String = "",
    tolerate: (JsonElement?, JsonElement?) -> Boolean = { _, _ -> false },
): String? = when {
    expected == actual -> null
    tolerate(expected, actual) -> null
    expected is JsonObject && actual is JsonObject -> (expected.keys + actual.keys).firstNotNullOfOrNull {
        jsonDiff(expected[it], actual[it], "$at/$it", tolerate)
    }
    expected is JsonArray && actual is JsonArray ->
        if (expected.size != actual.size) "$at: ${expected.size} items != ${actual.size} items"
        else expected.indices.firstNotNullOfOrNull { jsonDiff(expected[it], actual[it], "$at/$it", tolerate) }
    expected is JsonPrimitive && actual is JsonPrimitive && expected !is JsonNull && actual !is JsonNull &&
        !expected.isString && !actual.isString &&
        expected.content.toBigDecimalOrNull()?.let { e -> actual.content.toBigDecimalOrNull()?.compareTo(e) == 0 } == true -> null
    else -> "$at: $expected != $actual"
}

private fun String.toBigDecimalOrNull() = runCatching { BigDecimal(this) }.getOrNull()
