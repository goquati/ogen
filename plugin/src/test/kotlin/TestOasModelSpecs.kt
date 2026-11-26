import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SchemaLocation
import com.networknt.schema.SpecVersion
import de.quati.ogen.plugin.intern.oas.Diagnostic
import de.quati.ogen.plugin.intern.oas.decode.OasDecoder
import de.quati.ogen.plugin.intern.oas.model.OpenApi
import de.quati.ogen.plugin.intern.oas.read.SourceReader
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Cross-checks the model against the official OpenAPI JSON schemas with every spec in the repository:
 * a spec the official schema accepts must decode without errors and encode back to what it was.
 */
class TestOasModelSpecs {
    private val schemaIds = mapOf(
        "3.0" to "https://spec.openapis.org/oas/3.0/schema/2024-10-18",
        "3.1" to "https://spec.openapis.org/oas/3.1/schema/2025-09-15",
        "3.2" to "https://spec.openapis.org/oas/3.2/schema/2025-09-17",
    )
    private val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012) { builder ->
        builder.schemaMappers { mappers ->
            mappers.mappings(schemaIds.entries.associate { (version, id) ->
                id to "classpath:oas-schemas/$version-schema-${id.substringAfterLast('/')}.json"
            })
        }
    }
    private val schemas = mutableMapOf<String, JsonSchema>()
    private fun officialSchema(version: String): JsonSchema? {
        val minor = version.split('.').take(2).joinToString(".")
        val id = schemaIds[minor] ?: return null
        return schemas.getOrPut(minor) { factory.getSchema(SchemaLocation.of(id)) }
    }

    private val jackson = ObjectMapper()

    // Specs ogen accepts on purpose although the official schema does not; a new entry needs a decision.
    private val deliberatelyLenient = mapOf(
        "success-response-present.yaml" to "status code '2xx' in lowercase, as HttpCode.parse accepts it",
        // Schema contents are left to ogen's own checks, which name the problem better.
        "invalid-type.yaml" to "an unknown 'type' value; InvalidTypeCheck reports it",
        "validator-warnings.yaml" to "an unknown 'type' value; InvalidTypeCheck reports it",
        "pattern-invalid.yaml" to "an invalid regular expression; PatternCheck reports it",
    )

    // Until migration (phase 4), 3.0's boolean exclusiveMinimum/exclusiveMaximum do not fit the 3.2 model.
    private fun isPreMigration30(diagnostic: Diagnostic, version: String) = version.startsWith("3.0") &&
        diagnostic.location?.pointer?.segments?.lastOrNull() in setOf("exclusiveMinimum", "exclusiveMaximum") &&
        "found the boolean" in diagnostic.message

    // Differences that are no loss: a missing value written as null, and a single type written without array.
    private fun noLoss(expected: JsonElement?, actual: JsonElement?) =
        (expected is JsonNull && actual == null) ||
            (expected is JsonArray && expected.size == 1 && expected[0] is JsonPrimitive && actual == expected[0])

    @Test
    fun `official schemas reject invalid documents`() {
        listOf("3.0.3", "3.1.0", "3.2.0").forEach { version ->
            val document = """{"openapi": "$version", "info": {"title": "t", "version": "1", "titel": "x"}, "paths": {}}"""
            val errors = officialSchema(version)!!.validate(jackson.readTree(document))
            assertTrue(errors.isNotEmpty(), "the $version schema accepts an unknown field in info")
        }
    }

    @Test
    fun `specs the official schema accepts decode without errors and round-trip`() {
        val problems = mutableListOf<String>()
        val lenient = mutableListOf<String>()
        var checked = 0
        var roundTrips = 0
        for (path in repositorySpecFiles()) {
            val read = SourceReader.read(path)
            val root = read.element as? JsonObject ?: continue
            val version = (root["openapi"] as? JsonPrimitive)?.content ?: continue
            val official = officialSchema(version) ?: continue
            checked++
            val officialErrors = official.validate(jackson.readTree(root.toString()))
            val result = OasDecoder.decode(read)
            val errors = result.diagnostics
                .filter { it.severity == Diagnostic.Severity.ERROR && !isPreMigration30(it, version) }
            if (officialErrors.isEmpty() && errors.isNotEmpty())
                problems += "$path: valid for the official schema, but: ${errors.joinToString("; ")}"
            if (officialErrors.isNotEmpty() && result.diagnostics.none { it.severity == Diagnostic.Severity.ERROR })
                lenient += path.fileName.toString().also {
                    println("  lenient: $path: ${officialErrors.take(3).joinToString("; ") { e -> e.message }}")
                }
            val api = result.value
            if (api == null) {
                println("  no round trip: $path: ${result.diagnostics.take(2).joinToString("; ")}")
                continue
            }
            roundTrips++
            jsonDiff(root, OasDecoder.encode(api, OpenApi.serializer()), tolerate = ::noLoss)
                ?.let { problems += "$path: round trip differs at $it" }
        }
        println("Checked $checked specs, $roundTrips round trips")
        assertTrue(checked > 50, "only $checked specs found")
        assertEquals(deliberatelyLenient.keys, lenient.toSet(), "specs accepted although the official schema rejects them")
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }
}
