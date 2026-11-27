import de.quati.ogen.plugin.intern.oas.OasParseResult
import de.quati.ogen.plugin.intern.oas.OasParser
import de.quati.ogen.plugin.intern.oas.load.ResourceLoader
import de.quati.ogen.plugin.intern.oas.read.SourceReader
import de.quati.ogen.plugin.intern.oas.ref.JsonPointer
import de.quati.ogen.plugin.intern.oas.ref.at
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class TestBundler {
    private fun parse(root: String, vararg others: Pair<String, String>): OasParseResult {
        val files = (others.toList() + ("api/root.yaml" to root)).associate { (name, text) -> URI("mem:/$name") to text }
        return OasParser.parse(URI("mem:/api/root.yaml"), ResourceLoader { uri ->
            files[uri]?.let { ResourceLoader.Resource.Text(it, uri.path.removePrefix("/")) }
                ?: ResourceLoader.Resource.Failure("no file $uri")
        })
    }

    // The expected names and their order were checked against swagger-parser while the plugin still used it (3.0,
    // the only version it bundled external references for).
    private val header = "openapi: 3.0.3\ninfo: {title: API, version: '1'}\n"

    private fun OasParseResult.at(pointer: String): JsonElement? = bundle!!.element.at(JsonPointer.parse(pointer))
    private fun OasParseResult.names(kind: String) = (at("/components/$kind") as? JsonObject)?.keys?.toList()
    private fun json(text: String) = Json.parseToJsonElement(text)

    @Test
    fun `external targets move into components`() {
        val result = parse(
            header + """
            paths:
              /users:
                get:
                  parameters: [{${'$'}ref: '../common.yaml#/components/parameters/Limit'}]
                  responses:
                    '200':
                      description: ok
                      content:
                        application/json:
                          schema: {${'$'}ref: '../common.yaml#/components/schemas/User'}
                    default: {${'$'}ref: '../common.yaml#/components/responses/Error'}
            components:
              schemas:
                Local: {properties: {u: {${'$'}ref: '#/components/schemas/Local'}}}
            """.trimIndent(),
            "common.yaml" to """
                components:
                  parameters:
                    Limit: {name: limit, in: query, schema: {${'$'}ref: '#/components/schemas/Count'}}
                  responses:
                    Error: {description: error, content: {application/json: {schema: {${'$'}ref: 'errors.yaml'}}}}
                  schemas:
                    User: {properties: {id: {${'$'}ref: '#/components/schemas/Id'}, friend: {${'$'}ref: '#/components/schemas/User'}}}
                    Id: {type: string}
                    Count: {type: integer}
            """.trimIndent(),
            "errors.yaml" to "type: object\nproperties:\n  message: {type: string}\n",
        )
        assertEquals(emptyList(), result.diagnostics.map { it.toString() })
        assertNotNull(result.openApi)
        assertEquals(listOf("Local", "Count", "User", "Id", "errors"), result.names("schemas"))
        assertEquals(listOf("Limit"), result.names("parameters"))
        assertEquals(listOf("Error"), result.names("responses"))
        assertEquals(json("""{"${'$'}ref": "#/components/parameters/Limit"}"""), result.at("/paths/~1users/get/parameters/0"))
        assertEquals(json("""{"${'$'}ref": "#/components/schemas/Local"}"""), result.at("/components/schemas/Local/properties/u"))
        assertEquals(
            json("""{"properties": {"id": {"${'$'}ref": "#/components/schemas/Id"}, "friend": {"${'$'}ref": "#/components/schemas/User"}}}"""),
            result.at("/components/schemas/User"),
        )
        assertEquals(json("""{"${'$'}ref": "#/components/schemas/errors"}"""), result.at("/components/responses/Error/content/application~1json/schema"))
    }

    @Test
    fun `a component that only refers to another file is replaced by the target`() {
        val result = parse(
            header + """
            components:
              schemas:
                User: {${'$'}ref: 'common.yaml#/components/schemas/User'}
                Person: {${'$'}ref: 'common.yaml#/components/schemas/User'}
              securitySchemes:
                jwt: {${'$'}ref: 'common.yaml#/components/securitySchemes/jwt'}
            """.trimIndent(),
            "api/common.yaml" to """
                components:
                  schemas:
                    User: {type: object}
                  securitySchemes:
                    jwt: {type: http, scheme: bearer}
            """.trimIndent(),
        )
        assertEquals(emptyList(), result.diagnostics.map { it.toString() })
        assertEquals(listOf("User", "Person"), result.names("schemas"))
        assertEquals(json("""{"type": "object"}"""), result.at("/components/schemas/User"))
        assertEquals(json("""{"${'$'}ref": "#/components/schemas/User"}"""), result.at("/components/schemas/Person"))
        assertEquals(json("""{"type": "http", "scheme": "bearer"}"""), result.at("/components/securitySchemes/jwt"))
    }

    @Test
    fun `names that are taken get a suffix, equal content is shared`() {
        val result = parse(
            header + """
            components:
              schemas:
                Pet: {type: string}
                A: {${'$'}ref: 'a.yaml#/Pet'}
                B: {${'$'}ref: 'b.yaml#/Pet'}
                C: {${'$'}ref: 'c.yaml#/pet'}
                E: {${'$'}ref: 'my.pet.yaml'}
            """.trimIndent(),
            "api/a.yaml" to "Pet: {type: object, title: a}\n",
            "api/b.yaml" to "Pet: {type: object, title: b}\n",
            "api/c.yaml" to "pet: {type: object, title: c}\n",
            "api/my.pet.yaml" to "type: boolean\n",
        )
        assertEquals(emptyList(), result.diagnostics.map { it.toString() })
        assertEquals(listOf("Pet", "A", "B", "C", "E", "Pet_1", "Pet_2", "pet_3", "mypet"), result.names("schemas"))
        assertEquals(json("""{"${'$'}ref": "#/components/schemas/pet_3"}"""), result.at("/components/schemas/C"))
    }

    @Test
    fun `a target with the content of an existing component shares it`() {
        // swagger-parser loses the existing component 'Same' here, although 'D' then refers to it; ogen keeps it.
        val result = parse(
            header + "components:\n  schemas:\n    Same: {type: integer}\n    D: {\$ref: 'c.yaml#/Same'}\n",
            "api/c.yaml" to "Same: {type: integer}\n"
        )
        assertEquals(listOf("Same", "D"), result.names("schemas"))
        assertEquals(json("""{"${'$'}ref": "#/components/schemas/Same"}"""), result.at("/components/schemas/D"))
    }

    @Test
    fun `path items are inlined, discriminator mappings rewritten`() {
        val result = parse(
            header + """
            paths:
              /pets:
                ${'$'}ref: 'paths.yaml#/paths/~1pets'
            """.trimIndent(),
            "api/paths.yaml" to """
                paths:
                  /pets:
                    get:
                      responses:
                        '200':
                          description: ok
                          content:
                            application/json:
                              schema:
                                oneOf: [{${'$'}ref: 'pets.yaml#/Cat'}]
                                discriminator: {propertyName: kind, mapping: {cat: 'pets.yaml#/Cat', dog: Dog}}
            """.trimIndent(),
            "api/pets.yaml" to "Cat: {type: object}\n",
        )
        assertEquals(emptyList(), result.diagnostics.map { it.toString() })
        val schema = "/paths/~1pets/get/responses/200/content/application~1json/schema"
        assertEquals(
            json("""{"oneOf": [{"${'$'}ref": "#/components/schemas/Cat"}], "discriminator": {"propertyName": "kind", "mapping": {"cat": "#/components/schemas/Cat", "dog": "Dog"}}}"""),
            result.at(schema),
        )
    }

    @Test
    fun `fields next to a path item reference are merged into it`() {
        val result = parse(
            header + """
            paths:
              /pets:
                ${'$'}ref: 'paths.yaml#/Pets'
                parameters:
                  - ${'$'}ref: 'params.yaml#/Q'
            """.trimIndent(),
            "api/paths.yaml" to "Pets:\n  get:\n    responses: {'200': {description: ok}}\n",
            "api/params.yaml" to "Q: {name: q, in: query, schema: {type: string}}\n",
        )
        assertEquals(emptyList(), result.diagnostics.map { it.toString() })
        assertEquals(
            json("""[{"${'$'}ref": "#/components/parameters/Q"}]"""),
            result.at("/paths/~1pets/parameters"),
        )
        assertNotNull(result.at("/paths/~1pets/get"))
        assertEquals(null, result.at("/paths/~1pets/${'$'}ref"))
        assertEquals(1, result.openApi!!.paths!!.pathItems.getValue("/pets").parameters!!.size)
    }

    @Test
    fun `a field both next to a path item reference and in it warns, the one next to the reference wins`() {
        val result = parse(
            header + """
            paths:
              /pets:
                ${'$'}ref: 'paths.yaml#/Pets'
                summary: outer
                description: same
            """.trimIndent(),
            "api/paths.yaml" to "Pets:\n  summary: inner\n  description: same\n  get:\n    responses: {'200': {description: ok}}\n",
        )
        assertEquals(
            listOf(
                "api/root.yaml:6:5 (#/paths/~1pets/summary): 'summary' is both next to the Path Item ${'$'}ref and in the " +
                    "referenced Path Item; the one next to the ${'$'}ref is used"
            ),
            result.diagnostics.map { it.toString() },
        )
        assertEquals(json("\"outer\""), result.at("/paths/~1pets/summary"))
    }

    @Test
    fun `problems in fields next to a path item reference point to the file of the reference`() {
        val result = parse(
            header + """
            paths:
              /pets:
                ${'$'}ref: 'paths.yaml#/Pets'
                parameters:
                  - {name: q, in: query, required: maybe, schema: {type: string}}
            """.trimIndent(),
            "api/paths.yaml" to "Pets:\n  get:\n    responses: {'200': {description: ok}}\n",
        )
        assertEquals(1, result.errors.size, result.errors.toString())
        assertTrue(result.errors.single().toString().startsWith("api/root.yaml:7:"), result.errors.toString())
    }

    @Test
    fun `problems in moved parts point to their own file`() {
        val result = parse(
            header + "components:\n  schemas:\n    A: {\$ref: 'common.yaml#/A'}\n",
            "api/common.yaml" to "A:\n  type: object\n  properties:\n    n: {type: number, minimum: low}\n",
        )
        assertEquals(
            listOf("api/common.yaml:4:32 (#/components/schemas/A/properties/n/minimum): expected a number, found the string 'low'"),
            result.errors.map { it.toString() },
        )
    }

    // --- every spec of the repository ---

    // Specs with mistakes, to show that code is still generated from them.
    private val invalidOnPurpose = setOf("lenient-generation.yaml")

    @Test
    fun `every spec parses`() {
        val problems = mutableListOf<String>()
        var parsed = 0
        for (path in repositorySpecFiles()) {
            val raw = SourceReader.read(path).element as? JsonObject ?: continue
            if ("openapi" !in raw || path.fileName.toString() in invalidOnPurpose) continue
            val result = OasParser.parse(path)
            if (result.errors.isNotEmpty() || result.openApi == null) problems += "$path: ${result.errors}"
            parsed++
        }
        assertTrue(parsed > 50, "only $parsed specs")
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }
}
