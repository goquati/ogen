import de.quati.ogen.plugin.EndpointInfo
import de.quati.ogen.plugin.SpecsConfigBuilder
import de.quati.ogen.plugin.intern.model.Spec
import de.quati.ogen.plugin.intern.model.config.SpecConfig
import de.quati.ogen.plugin.intern.oas.OasParser
import de.quati.ogen.plugin.intern.oas.read.SourceReader
import de.quati.ogen.plugin.intern.parsing.legacy.parseLegacy
import de.quati.ogen.plugin.intern.parsing.parse
import io.swagger.parser.OpenAPIParser
import io.swagger.v3.parser.core.models.ParseOptions
import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The golden test of the new parser (plan, phase 6): for every spec in the repository, the internal [Spec] that
 * code generation works on must be the same as with swagger-parser and the legacy parsing.
 * Goes away with swagger-parser (phase 8).
 */
class TestParsingGolden {
    // YAML 1.1 (swagger-parser) reads unquoted yes/no/on/off as booleans; YAML 1.2 reads them as strings.
    private val yaml11Booleans = setOf(
        "enum-values-boolean-cast.yaml",
        "enum-values-yaml-booleans.yaml",
        "enum-values-yaml-booleans-3.1.yaml",
    )

    private fun config(path: Path, operationIds: ((EndpointInfo) -> String?)?): SpecConfig = SpecsConfigBuilder().apply {
        utilPackageName("de.quati.ogen.test.gen.util")
        add(packageName = "de.quati.ogen.test.gen") {
            specFile(path.toAbsolutePath().toString())
            model {}
            operationIds?.let { operationIdGenerator(it) }
        }
    }.build().specs.single()

    private fun legacy(path: Path, config: SpecConfig): Result<Spec> = runCatching {
        OpenAPIParser().readLocation(path.toString(), null, ParseOptions().apply {
            isResolve = true
            isInferSchemaType = true
        }).parseLegacy(config)
    }

    private fun new(path: Path, config: SpecConfig): Result<Spec> = runCatching {
        val result = OasParser.parse(path)
        val api = result.openApi ?: error("not parsed: ${result.errors.joinToString("; ")}")
        api.parse(result.sourceVersion!!, config)
    }

    @Test
    fun `the new parser gives the same spec as swagger-parser`() = compareAll(operationIds = null)

    @Test
    fun `the same with generated operation ids`() = compareAll(operationIds = { info ->
        "${info.method.name.lowercase()}_${info.tag}_${info.relativePath}"
    })

    private fun compareAll(operationIds: ((EndpointInfo) -> String?)?) {
        val problems = mutableListOf<String>()
        var compared = 0
        for (path in repositorySpecFiles()) {
            if (path.name in yaml11Booleans) continue
            val raw = SourceReader.read(path).element as? JsonObject ?: continue
            if ("openapi" !in raw) continue
            val config = config(path, operationIds)
            val expected = legacy(path, config)
            val actual = new(path, config)
            compared++
            when {
                expected.isFailure && actual.isFailure -> Unit
                expected.isFailure -> problems += "$path: swagger-parser failed (${expected.exceptionOrNull()?.message}), the new parser did not"
                actual.isFailure -> problems += "$path: the new parser failed: ${actual.exceptionOrNull()?.message}"
                else -> specDiff(expected.getOrThrow(), actual.getOrThrow())?.let { problems += "$path: $it" }
            }
        }
        println("Compared $compared specs")
        assertTrue(compared > 50, "only $compared specs")
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }

    // swagger-parser read decimals as double, so `0.010` became 0.01; the new parser keeps the exact value.
    private val decimal = Regex("""(multipleOf=|value=)(-?[0-9.]+(?:E[-+]?[0-9]+)?)""")
    // Sets print in insertion order, but compare without it; sorting `required` shows the real difference.
    private val required = Regex("""required=\[([^\]]*)]""")
    private fun numbersByValue(text: String) = decimal.replace(text) { m ->
        m.groupValues[1] + java.math.BigDecimal(m.groupValues[2]).stripTrailingZeros().toPlainString()
    }.let { normalized -> required.replace(normalized) { m -> "required=[" + m.groupValues[1].split(", ").sorted().joinToString(", ") + "]" } }

    /** The first difference, named as precisely as possible. */
    private fun specDiff(expected: Spec, actual: Spec): String? {
        if (expected == actual) return null
        if (numbersByValue(expected.toString()) == numbersByValue(actual.toString())) return null
        if (expected.version != actual.version) return "version ${expected.version} != ${actual.version}"
        if (expected.security != actual.security) return "security ${expected.security} != ${actual.security}"
        mapDiff("schemas", expected.components.schemas, actual.components.schemas)?.let { return it }
        mapDiff("schema aliases", expected.components.schemaAliases, actual.components.schemaAliases)?.let { return it }
        mapDiff("parameters", expected.components.parameters, actual.components.parameters)?.let { return it }
        mapDiff("request bodies", expected.components.requestBody, actual.components.requestBody)?.let { return it }
        mapDiff("responses", expected.components.response, actual.components.response)?.let { return it }
        val e = expected.paths.paths
        val a = actual.paths.paths
        if (e.map { "${it.method} ${it.path}" } != a.map { "${it.method} ${it.path}" })
            return "endpoints ${e.map { "${it.method} ${it.path}" }} != ${a.map { "${it.method} ${it.path}" }}"
        e.zip(a).firstOrNull { (x, y) -> x != y && numbersByValue(x.toString()) != numbersByValue(y.toString()) }?.let { (x, y) ->
            return "endpoint ${x.method} ${x.path}: ${textDiff(x.toString(), y.toString())}"
        }
        if (expected.multipartBodySchemas != actual.multipartBodySchemas)
            return "multipart body schemas ${expected.multipartBodySchemas} != ${actual.multipartBodySchemas}"
        return "specs differ"
    }

    private fun <K, V> mapDiff(what: String, expected: Map<K, V>, actual: Map<K, V>): String? {
        if (expected.keys.toList() != actual.keys.toList()) {
            val missing = expected.keys - actual.keys
            val extra = actual.keys - expected.keys
            val firstMismatch = expected.keys.zip(actual.keys).indexOfFirst { (x, y) -> x != y }
                .let { if (it < 0) minOf(expected.size, actual.size) else it }
            return "$what differ: missing $missing, extra $extra, first order difference at $firstMismatch: " +
                "${expected.keys.drop(firstMismatch).take(3)} != ${actual.keys.drop(firstMismatch).take(3)}"
        }
        return expected.keys.firstOrNull { expected[it] != actual[it] && numbersByValue(expected[it].toString()) != numbersByValue(actual[it].toString()) }
            ?.let { "$what '$it': ${textDiff(expected[it].toString(), actual[it].toString())}" }
    }

    /** Both texts around their first difference. */
    private fun textDiff(expected: String, actual: String): String {
        val at = expected.zip(actual).indexOfFirst { (x, y) -> x != y }.let { if (it < 0) minOf(expected.length, actual.length) else it }
        fun around(text: String) = "…" + text.substring(maxOf(0, at - 200), minOf(text.length, at + 150)) + "…"
        return "\n  swagger: ${around(expected)}\n  new:     ${around(actual)}"
    }
}
