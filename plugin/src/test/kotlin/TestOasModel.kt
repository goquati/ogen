import de.quati.ogen.plugin.intern.oas.Diagnostic
import de.quati.ogen.plugin.intern.oas.OasVersion
import de.quati.ogen.plugin.intern.oas.decode.DecodeResult
import de.quati.ogen.plugin.intern.oas.decode.OasDecoder
import de.quati.ogen.plugin.intern.oas.model.OpenApi
import de.quati.ogen.plugin.intern.oas.model.Parameter
import de.quati.ogen.plugin.intern.oas.model.RefOr
import de.quati.ogen.plugin.intern.oas.model.Schema
import de.quati.ogen.plugin.intern.oas.read.SourceReader
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestOasModel {
    private fun decode(yaml: String): DecodeResult<OpenApi> = OasDecoder.decode(SourceReader.read(yaml, "spec.yaml"), OasVersion.V3_1)

    private fun decodeOk(yaml: String): OpenApi {
        val result = decode(yaml)
        assertTrue(result.diagnostics.none { it.severity == Diagnostic.Severity.ERROR }, result.diagnostics.joinToString("\n"))
        return assertNotNull(result.value)
    }

    private fun messages(yaml: String): List<String> = decode(yaml).diagnostics.map { "${it.severity}: $it" }

    private val header = "openapi: 3.1.0\ninfo:\n  title: API\n  version: '1'\n"

    private fun schemas(yaml: String) = decodeOk("${header}components:\n  schemas:\n${yaml.prependIndent("    ")}")
        .components!!.schemas!!

    // --- decoding ---

    @Test
    fun `minimal document`() {
        val api = decodeOk(header)
        assertEquals("3.1.0", api.openapi)
        assertEquals("API", api.info?.title)
        assertNull(api.paths)
        assertNull(api.security)
    }

    @Test
    fun `unquoted version is read as text`() {
        // A float, as swagger-parser read it: 1.10 is 1.1.
        assertEquals("1.1", decodeOk("openapi: 3.1.0\ninfo:\n  title: API\n  version: 1.10\n").info?.version)
    }

    @Test
    fun `empty and absent stay apart`() {
        assertEquals(emptyList(), decodeOk("${header}security: []\n").security)
    }

    @Test
    fun `extensions are collected`() {
        val api = decodeOk("${header}x-root: 1\npaths:\n  x-paths: true\n  /a:\n    x-item: {a: b}\n    get:\n      x-op: [1]\n")
        assertEquals(setOf("x-root"), api.extensions.keys)
        val paths = assertNotNull(api.paths)
        assertEquals(setOf("x-paths"), paths.extensions.keys)
        assertEquals(setOf("/a"), paths.pathItems.keys)
        assertEquals(setOf("x-item"), paths.pathItems.getValue("/a").extensions.keys)
        assertEquals(setOf("x-op"), paths.pathItems.getValue("/a").get!!.extensions.keys)
    }

    @Test
    fun `references and objects`() {
        val api = decodeOk(
            header + """
            paths:
              /a/{id}:
                parameters:
                  - ${'$'}ref: '#/components/parameters/Id'
                    description: The id
                  - name: q
                    in: query
            """.trimIndent()
        )
        val parameters = api.paths!!.pathItems.getValue("/a/{id}").parameters!!
        assertEquals(RefOr.Ref("#/components/parameters/Id", description = "The id"), parameters[0])
        val inline = assertIs<RefOr.Value<Parameter>>(parameters[1]).value
        assertEquals(Parameter.Location.QUERY, inline.location)
    }

    @Test
    fun `schemas keep ref siblings, unknown keywords and booleans`() {
        val s = schemas(
            """
            A:
              ${'$'}ref: '#/components/schemas/B'
              description: next to the ref
            B:
              type: [string, 'null']
              nullable: true
              x-kotlin: {name: Bee}
              if: {required: [a]}
            C: true
            D:
              type: object
              additionalProperties: false
              properties:
                n: {type: number, minimum: 0.1000000000000000055511151231257827, default: null}
            """.trimIndent()
        )
        val a = assertIs<Schema.Obj>(s["A"])
        assertEquals("#/components/schemas/B", a.ref)
        assertEquals("next to the ref", a.description)
        val b = assertIs<Schema.Obj>(s["B"])
        assertEquals(listOf("string", "null"), b.type)
        assertEquals(setOf("nullable"), b.other.keys)
        assertEquals(Schema.Obj(required = listOf("a")), b.ifSchema)
        assertEquals(setOf("x-kotlin"), b.extensions.keys)
        assertEquals(Schema.Bool(true), s["C"])
        val d = assertIs<Schema.Obj>(s["D"])
        assertEquals(Schema.Bool(false), d.additionalProperties)
        val n = assertIs<Schema.Obj>(d.properties!!.getValue("n"))
        assertEquals(BigDecimal("0.1"), n.minimum)
        assertEquals(JsonNull, n.default)
    }

    @Test
    fun `encoding gives the input back`() {
        val yaml = header + """
            x-top: 1
            paths:
              /a:
                get:
                  responses:
                    '200':
                      description: ok
                      content:
                        application/json:
                          schema:
                            type: object
                            properties:
                              a: {type: string, x-a: 1, const: null}
                              b: {${'$'}ref: '#/components/schemas/B', description: b}
                    x-r: 2
            components:
              schemas:
                B: {type: [integer, 'null'], maximum: 1e3, if: {}}
            """.trimIndent()
        val read = SourceReader.read(yaml, "spec.yaml")
        val api = assertNotNull(OasDecoder.decode(read, OasVersion.V3_1).value)
        assertEquals(null, jsonDiff(read.element, OasDecoder.encode(api, OpenApi.serializer())))
    }

    @Test
    fun `single type is written without array`() {
        val s = schemas("A: {type: [string]}")
        val encoded = OasDecoder.encode(s.getValue("A"), Schema.serializer())
        assertEquals(JsonPrimitive("string"), encoded.jsonObject["type"])
    }

    // --- structure check ---

    @Test
    fun `unknown fields are errors with position`() {
        assertEquals(
            listOf("ERROR: spec.yaml:4:3 (#/info/titel): unknown field 'titel' in Info"),
            messages("openapi: 3.1.0\ninfo:\n  title: API\n  titel: API\n  version: '1'\n"),
        )
    }

    @Test
    fun `missing required fields are errors`() {
        assertEquals(
            listOf(
                "ERROR: spec.yaml:3:3 (#/info): missing required field 'title' in Info",
                "ERROR: spec.yaml:8:11 (#/paths/~1a/get/parameters/0): missing required field 'in' in Parameter",
            ),
            messages("openapi: 3.1.0\ninfo:\n  version: '1'\npaths:\n  /a:\n    get:\n      parameters:\n        - name: a\n"),
        )
    }

    @Test
    fun `all problems are reported at once`() {
        val result = messages(
            header + """
            paths:
              a: {}
              /b:
                get:
                  deprecated: 'yes'
                  tags: tag
                  parameters:
                    - {name: a, in: qeury}
                  responses:
                    '200': {description: ok}
                    '600': {description: no}
                    '2xx': {description: lowercase is fine}
                    ok: {description: no}
            components:
              schemas:
                S: {type: 1, minimum: '1', maxLength: 1.5, properties: []}
                T: 'string'
            """.trimIndent()
        )
        assertEquals(
            listOf(
                "ERROR: spec.yaml:6:3 (#/paths/a): path 'a' must start with '/'",
                "ERROR: spec.yaml:9:19 (#/paths/~1b/get/deprecated): expected a boolean, found the string 'yes'",
                "ERROR: spec.yaml:10:13 (#/paths/~1b/get/tags): expected an array, found the string 'tag'",
                "ERROR: spec.yaml:12:25 (#/paths/~1b/get/parameters/0/in): 'qeury' is not one of query, querystring, header, path, cookie",
                "ERROR: spec.yaml:15:9 (#/paths/~1b/get/responses/600): '600' is not a status code like 200 or 4XX, nor 'default'",
                "ERROR: spec.yaml:17:9 (#/paths/~1b/get/responses/ok): 'ok' is not a status code like 200 or 4XX, nor 'default'",
                "ERROR: spec.yaml:20:15 (#/components/schemas/S/type): expected a string or an array of strings, found the number 1",
                "ERROR: spec.yaml:20:27 (#/components/schemas/S/minimum): expected a number, found the string '1'",
                "ERROR: spec.yaml:20:43 (#/components/schemas/S/maxLength): expected an integer, found the number 1.5",
                "ERROR: spec.yaml:20:60 (#/components/schemas/S/properties): expected an object, found an array",
                "ERROR: spec.yaml:21:8 (#/components/schemas/T): expected a schema (an object or a boolean), found the string 'string'",
            ),
            result,
        )
    }

    @Test
    fun `siblings of a reference are ignored`() {
        val result = decode(
            header + "components:\n  parameters:\n    P:\n      \$ref: '#/components/parameters/Q'\n      name: p\n"
        )
        assertNotNull(result.value)
        assertEquals(emptyList(), result.diagnostics)
    }

    @Test
    fun `schemas accept unknown keywords`() {
        assertEquals(emptyList(), messages(header + "components:\n  schemas:\n    A: {foo: 1, nullable: true, x-a: 1}\n"))
    }
}
