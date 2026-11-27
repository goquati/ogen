import de.quati.ogen.plugin.intern.oas.Diagnostic
import de.quati.ogen.plugin.intern.oas.OasVersion
import de.quati.ogen.plugin.intern.oas.load.LoadedSpec
import de.quati.ogen.plugin.intern.oas.load.OasLoader
import de.quati.ogen.plugin.intern.oas.load.ResourceLoader
import de.quati.ogen.plugin.intern.oas.read.SourceReader
import de.quati.ogen.plugin.intern.oas.ref.JsonPointer
import de.quati.ogen.plugin.intern.oas.ref.at
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TestOasMigration {
    private fun load(root: String, vararg others: Pair<String, String>): LoadedSpec {
        val files = (others.toList() + ("root.yaml" to root)).associate { (name, text) -> URI("mem:/$name") to text }
        return OasLoader { uri ->
            files[uri]?.let { ResourceLoader.Resource.Text(it, uri.path.removePrefix("/")) }
                ?: ResourceLoader.Resource.Failure("no file $uri")
        }.load(URI("mem:/root.yaml"))
    }

    private fun spec30(schemas: String) =
        "openapi: 3.0.3\ninfo: {title: API, version: '1'}\npaths: {}\ncomponents:\n  schemas:\n${schemas.trimIndent().prependIndent("    ")}\n"

    private fun LoadedSpec.at(pointer: String): JsonElement? = root!!.element.at(JsonPointer.parse(pointer))
    private fun LoadedSpec.schema(name: String) = at("/components/schemas/$name")
    private fun LoadedSpec.messages() = diagnostics.map { "${it.severity}: $it" }
    private fun json(text: String) = Json.parseToJsonElement(text)

    @Test
    fun `versions are detected`() {
        assertEquals(OasVersion.V3_0, OasVersion.parse("3.0.3"))
        assertEquals(OasVersion.V3_0, OasVersion.parse("3.0"))
        assertEquals(OasVersion.V3_1, OasVersion.parse("3.1.1"))
        assertEquals(OasVersion.V3_2, OasVersion.parse("3.2.0"))
        assertEquals(OasVersion.V3_1, OasVersion.parse("3.1.0-rc1"))
        assertNull(OasVersion.parse("2.0"))
        assertNull(OasVersion.parse("3.3.0"))
        assertNull(OasVersion.parse("three"))
    }

    @Test
    fun `unsupported versions are errors`() {
        assertEquals(
            listOf("ERROR: root.yaml:1:1 (#): Swagger 2.0 is not supported; convert the spec to OpenAPI 3"),
            load("swagger: '2.0'\ninfo: {title: API, version: '1'}\n").messages(),
        )
        assertEquals(
            listOf("ERROR: root.yaml:1:10 (#/openapi): OpenAPI 4.0.0 is not supported; supported are 3.0, 3.1, 3.2"),
            load("openapi: 4.0.0\ninfo: {title: API, version: '1'}\n").messages(),
        )
    }

    @Test
    fun `version is set to 3_2 and the source version kept`() {
        val spec = load(spec30("A: {type: string}"))
        assertEquals(OasVersion.V3_0, spec.sourceVersion)
        assertEquals(JsonPrimitive("3.2.0"), spec.at("/openapi"))
        val spec31 = load("openapi: 3.1.0\ninfo: {title: API, version: '1'}\ncomponents:\n  schemas:\n    A: {type: string, nullable: true}\n")
        assertEquals(OasVersion.V3_1, spec31.sourceVersion)
        assertEquals(JsonPrimitive("3.2.0"), spec31.at("/openapi"))
        // 3.1 has no 'nullable'; it is left as an unknown keyword.
        assertEquals(json("""{"type": "string", "nullable": true}"""), spec31.schema("A"))
    }

    @Test
    fun `nullable becomes a null type`() {
        val spec = load(spec30("""
            A: {type: string, nullable: true, maxLength: 3}
            B: {type: string, nullable: false}
            C: {type: string, enum: [a, b], nullable: true}
            D: {type: string, enum: [a, null], nullable: true}
            E: {items: {type: string}, nullable: true}
            F: {additionalProperties: {type: integer}, nullable: true}
            G: {enum: [1, 2], nullable: true}
            H: {enum: [true], nullable: true}
        """))
        assertEquals(emptyList(), spec.messages())
        assertEquals(json("""{"type": ["string", "null"], "maxLength": 3}"""), spec.schema("A"))
        assertEquals(json("""{"type": "string"}"""), spec.schema("B"))
        assertEquals(json("""{"type": ["string", "null"], "enum": ["a", "b"]}"""), spec.schema("C"))
        assertEquals(json("""{"type": ["string", "null"], "enum": ["a", null]}"""), spec.schema("D"))
        assertEquals(json("""{"items": {"type": "string"}, "type": ["array", "null"]}"""), spec.schema("E"))
        assertEquals(json("""{"additionalProperties": {"type": "integer"}, "type": ["object", "null"]}"""), spec.schema("F"))
        assertEquals(json("""{"enum": [1, 2], "type": ["number", "null"]}"""), spec.schema("G"))
        assertEquals(json("""{"enum": [true], "type": ["boolean", "null"]}"""), spec.schema("H"))
    }

    @Test
    fun `nullable compositions get a null alternative`() {
        val spec = load(spec30("""
            A: {oneOf: [{${'$'}ref: '#/components/schemas/X'}, {type: integer}], nullable: true}
            B: {anyOf: [{${'$'}ref: '#/components/schemas/X'}], nullable: true}
            C: {description: c, allOf: [{${'$'}ref: '#/components/schemas/X'}], nullable: true}
            D: {type: object, allOf: [{${'$'}ref: '#/components/schemas/X'}], nullable: true}
            X: {type: object}
        """))
        assertEquals(emptyList(), spec.messages())
        assertEquals(
            json("""{"oneOf": [{"${'$'}ref": "mem:/root.yaml#/components/schemas/X"}, {"type": "integer"}, {"type": "null"}]}"""),
            spec.schema("A"),
        )
        assertEquals(json("""{"anyOf": [{"${'$'}ref": "mem:/root.yaml#/components/schemas/X"}, {"type": "null"}]}"""), spec.schema("B"))
        assertEquals(
            json("""{"description": "c", "anyOf": [{"allOf": [{"${'$'}ref": "mem:/root.yaml#/components/schemas/X"}]}, {"type": "null"}]}"""),
            spec.schema("C"),
        )
        assertEquals(
            json("""{"type": ["object", "null"], "anyOf": [{"allOf": [{"${'$'}ref": "mem:/root.yaml#/components/schemas/X"}]}, {"type": "null"}]}"""),
            spec.schema("D"),
        )
    }

    @Test
    fun `nullable without anything to attach to is removed`() {
        val spec = load(spec30("A: {nullable: true, description: a}"))
        assertEquals(json("""{"description": "a"}"""), spec.schema("A"))
        assertEquals(emptyList(), spec.messages())
    }

    @Test
    fun `exclusive bounds become numbers`() {
        val spec = load(spec30("""
            A: {type: number, minimum: 1, exclusiveMinimum: true, maximum: 9, exclusiveMaximum: false}
            B: {type: number, exclusiveMaximum: true}
        """))
        assertEquals(json("""{"type": "number", "maximum": 9, "exclusiveMinimum": 1}"""), spec.schema("A"))
        assertEquals(json("""{"type": "number"}"""), spec.schema("B"))
        assertEquals(emptyList(), spec.messages())
    }

    @Test
    fun `keywords next to a reference are removed`() {
        val spec = load(spec30("""
            A: {${'$'}ref: '#/components/schemas/X', description: a, x-a: 1}
            B: {${'$'}ref: '#/components/schemas/X', nullable: true, type: object}
            X: {type: object}
        """))
        assertEquals(json("""{"${'$'}ref": "mem:/root.yaml#/components/schemas/X", "x-a": 1}"""), spec.schema("A"))
        assertEquals(json("""{"${'$'}ref": "mem:/root.yaml#/components/schemas/X"}"""), spec.schema("B"))
        assertEquals(emptyList(), spec.messages())
    }

    @Test
    fun `schemas everywhere are migrated, data is not`() {
        val spec = load(
            """
            openapi: 3.0.3
            info: {title: API, version: '1'}
            paths:
              /a:
                get:
                  parameters:
                    - {name: p, in: query, schema: {type: integer, nullable: true}}
                  responses:
                    '200':
                      description: ok
                      headers:
                        X: {schema: {type: string, nullable: true}}
                      content:
                        application/json:
                          schema:
                            type: object
                            properties:
                              list: {type: array, items: {type: string, nullable: true}}
                              map: {type: object, additionalProperties: {type: string, nullable: true}}
                              ext: {${'$'}ref: 'other.yaml#/Ext'}
                          example: {nullable: true, type: string}
            x-data: {nullable: true, type: string}
            """.trimIndent(),
            "other.yaml" to "Ext: {type: string, nullable: true}\nUnused: {type: string, nullable: true}\n",
        )
        assertEquals(emptyList(), spec.messages())
        val nullableString = json("""{"type": ["string", "null"]}""")
        val get = "/paths/~1a/get"
        assertEquals(json("""{"type": ["integer", "null"]}"""), spec.at("$get/parameters/0/schema"))
        assertEquals(nullableString, spec.at("$get/responses/200/headers/X/schema"))
        val body = "$get/responses/200/content/application~1json"
        assertEquals(nullableString, spec.at("$body/schema/properties/list/items"))
        assertEquals(nullableString, spec.at("$body/schema/properties/map/additionalProperties"))
        assertEquals(json("""{"nullable": true, "type": "string"}"""), spec.at("$body/example"))
        assertEquals(json("""{"nullable": true, "type": "string"}"""), spec.at("/x-data"))
        // A fragment of another file is migrated as the root's version; parts nothing refers to stay as they are.
        val other = spec.documents.getValue(URI("mem:/other.yaml")).element as JsonObject
        assertEquals(nullableString, other["Ext"])
        assertEquals(json("""{"type": "string", "nullable": true}"""), other["Unused"])
    }

    @Test
    fun `every repository spec migrates without errors`() {
        val problems = repositorySpecFiles().mapNotNull { path ->
            val element = SourceReader.read(path).element as? JsonObject ?: return@mapNotNull null
            if ("openapi" !in element) return@mapNotNull null
            OasLoader().load(path.toAbsolutePath().toUri()).diagnostics
                .filter { it.severity == Diagnostic.Severity.ERROR }
                .takeIf { it.isNotEmpty() }
                ?.let { "$path: ${it.joinToString("; ")}" }
        }
        assertEquals(emptyList(), problems)
    }
}
