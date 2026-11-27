import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SchemaLocation
import com.networknt.schema.SpecVersion
import de.quati.ogen.plugin.intern.oas.Diagnostic
import de.quati.ogen.plugin.intern.oas.decode.OasDecoder
import de.quati.ogen.plugin.intern.oas.model.OpenApi
import de.quati.ogen.plugin.intern.oas.load.OasLoader
import de.quati.ogen.plugin.intern.oas.read.SourceReader
import de.quati.ogen.plugin.intern.oas.ref.JsonPointer
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
    // The strict variant checks schema objects against the OpenAPI 3.2 dialect of JSON Schema 2020-12.
    private val strict32 = "https://spec.openapis.org/oas/3.2/schema-base/2025-09-17"
    private val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012) { builder ->
        builder.schemaMappers { mappers ->
            mappers.mappings(
                (schemaIds.values + strict32 + "https://spec.openapis.org/oas/3.2/dialect/2025-09-17" +
                    "https://spec.openapis.org/oas/3.2/meta/2025-09-17").associateWith { id ->
                    val (version, kind, date) = id.removePrefix("https://spec.openapis.org/oas/").split('/')
                    "classpath:oas-schemas/$version-$kind-$date.json"
                }
            )
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

    // Specs with schema contents only the strict 3.2 check sees as invalid; ogen's own checks report them.
    private val invalidSchemaContents = mapOf(
        "invalid-type-3.1.yaml" to "an unknown 'type' value; InvalidTypeCheck reports it",
        "invalid-type-list-3.1.yaml" to "an unknown 'type' value; InvalidTypeCheck reports it",
    )

    // Differences that are no loss: a missing value written as null, and a single type written without array.
    private fun noLoss(expected: JsonElement?, actual: JsonElement?) =
        (expected is JsonNull && actual == null) ||
            (expected is JsonArray && expected.size == 1 && expected[0] is JsonPrimitive && actual == expected[0])

    @Test
    fun `the strict 3_2 schema checks schema objects`() {
        val strict = factory.getSchema(SchemaLocation.of(strict32))
        val document = """{"openapi": "3.2.0", "info": {"title": "t", "version": "1"},
            "components": {"schemas": {"A": {"type": "number", "exclusiveMinimum": true}}}}"""
        assertTrue(strict.validate(jackson.readTree(document)).isNotEmpty(), "a boolean exclusiveMinimum passes")
        assertTrue(officialSchema("3.2.0")!!.validate(jackson.readTree(document)).isEmpty(), "the plain schema is loose")
    }

    @Test
    fun `official schemas reject invalid documents`() {
        listOf("3.0.3", "3.1.0", "3.2.0").forEach { version ->
            val document = """{"openapi": "$version", "info": {"title": "t", "version": "1", "titel": "x"}, "paths": {}}"""
            val errors = officialSchema(version)!!.validate(jackson.readTree(document))
            assertTrue(errors.isNotEmpty(), "the $version schema accepts an unknown field in info")
        }
    }

    /**
     * For each spec: load and migrate it, decode the migrated root. If the official schema of its own version
     * accepts the spec, there must be no error, the migrated root must be valid OpenAPI 3.2 for the official
     * 3.2 schema, and it must encode back to what it was.
     */
    @Test
    fun `specs the official schema accepts migrate to valid 3_2, decode without errors and round-trip`() {
        val problems = mutableListOf<String>()
        val lenient = mutableListOf<String>()
        val latest = factory.getSchema(SchemaLocation.of(strict32))
        var checked = 0
        var roundTrips = 0
        for (path in repositorySpecFiles()) {
            val raw = SourceReader.read(path).element as? JsonObject ?: continue
            val version = (raw["openapi"] as? JsonPrimitive)?.content ?: continue
            val official = officialSchema(version) ?: continue
            // Mistakes on purpose, to show that code is still generated from them (TestLenientGeneration).
            if (path.fileName.toString() == "lenient-generation.yaml") continue
            checked++
            val officialErrors = official.validate(jackson.readTree(raw.toString()))
            val loaded = OasLoader().load(path.toAbsolutePath().toUri())
            val root = loaded.root ?: continue.also { problems += "$path: not loaded: ${loaded.diagnostics}" }
            val result = OasDecoder.decode(root.element, OpenApi.serializer(), JsonPointer.ROOT, root::locationOf)
            val errors = (loaded.diagnostics + result.diagnostics).filter { it.severity == Diagnostic.Severity.ERROR }
            if (officialErrors.isEmpty()) {
                if (errors.isNotEmpty()) problems += "$path: valid for the official schema, but: ${errors.joinToString("; ")}"
                val migratedErrors = latest.validate(jackson.readTree(root.element.toString()))
                if (migratedErrors.isNotEmpty() && path.fileName.toString() !in invalidSchemaContents)
                    problems += "$path: migrated spec is not valid 3.2: ${migratedErrors.take(3).joinToString("; ") { it.message }}"
            } else if (errors.isEmpty()) {
                lenient += path.fileName.toString()
                println("  lenient: $path: ${officialErrors.take(3).joinToString("; ") { it.message }}")
            }
            // A spec with errors is decoded without the parts that do not fit; it cannot round-trip.
            val api = result.value.takeIf { errors.isEmpty() }
            if (api == null) {
                println("  no round trip: $path: ${result.diagnostics.take(2).joinToString("; ")}")
                continue
            }
            roundTrips++
            jsonDiff(root.element, OasDecoder.encode(api, OpenApi.serializer()), tolerate = ::noLoss)
                ?.let { problems += "$path: round trip differs at $it" }
        }
        println("Checked $checked specs, $roundTrips round trips")
        assertTrue(checked > 50, "only $checked specs found")
        assertEquals(checked, roundTrips, "every spec decodes")
        assertEquals(deliberatelyLenient.keys, lenient.toSet(), "specs accepted although the official schema rejects them")
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }
}
