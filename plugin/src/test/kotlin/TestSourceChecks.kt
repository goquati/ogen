import de.quati.ogen.plugin.intern.oas.OasParser
import de.quati.ogen.plugin.intern.oas.load.ResourceLoader
import de.quati.ogen.plugin.intern.validation.ValidatedSpec
import de.quati.ogen.plugin.intern.validation.exclusiveFlagWithoutBoundWarnings
import de.quati.ogen.plugin.intern.validation.getWithBodyWarnings
import de.quati.ogen.plugin.intern.validation.headerUnderscoreWarnings
import de.quati.ogen.plugin.intern.validation.ignoredNextToRefWarnings
import de.quati.ogen.plugin.intern.validation.invalidTypeErrors
import de.quati.ogen.plugin.intern.validation.nullableIn31Warnings
import de.quati.ogen.plugin.intern.validation.walkSchemas
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals

/** The checks that read the spec as its files write it, before migration, and the ported recommendations. */
class TestSourceChecks {
    private fun spec(yaml: String): ValidatedSpec {
        val result = OasParser.parse(URI("mem:/spec.yaml"), ResourceLoader { ResourceLoader.Resource.Text(yaml, "spec.yaml") })
        return ValidatedSpec(result.openApi ?: error(result.errors.toString()), result.sourceVersion!!, result.bundle)
    }

    private fun spec30(schemas: String, rest: String = "", components: String = "") = spec(
        "openapi: 3.0.3\ninfo: {title: t, version: '1'}\n$rest\ncomponents:\n${components.trimIndent().prependIndent("  ")}\n" +
            "  schemas:\n${schemas.trimIndent().prependIndent("    ")}\n"
    )

    @Test
    fun `exclusive flags without their bound`() {
        val spec = spec30("""
            A: {type: number, exclusiveMinimum: true}
            B: {type: number, minimum: 1, exclusiveMinimum: true, exclusiveMaximum: true}
            C: {type: number, maximum: 1, exclusiveMaximum: false}
        """)
        assertEquals(
            listOf(
                "'exclusiveMinimum: true' in 'A' has no effect without 'minimum'",
                "'exclusiveMaximum: true' in 'B' has no effect without 'maximum'",
            ),
            exclusiveFlagWithoutBoundWarnings(spec),
        )
    }

    @Test
    fun `keywords next to a reference`() {
        val spec = spec30(
            """
            A: {${'$'}ref: '#/components/schemas/X', description: fine, x-a: 1}
            B: {${'$'}ref: '#/components/schemas/X', nullable: true, type: object}
            C: {properties: {p: {${'$'}ref: '#/components/schemas/X', maxLength: 3}}}
            X: {type: object}
            """,
            rest = """
            paths:
              /a:
                get:
                  parameters:
                    - {${'$'}ref: '#/components/parameters/P', name: other, in: query}
                  responses:
                    '200': {${'$'}ref: '#/components/responses/R', description: fine}
            """.trimIndent(),
            components = """
            parameters:
              P: {name: p, in: query, schema: {type: string}}
            responses:
              R: {description: ok}
            """,
        )
        assertEquals(
            listOf(
                "'nullable', 'type' are ignored next to '\$ref' in 'B' in OpenAPI 3.0; a nullable \$ref goes in an 'allOf'",
                "'maxLength' is ignored next to '\$ref' in 'C.p' in OpenAPI 3.0",
                "'name', 'in' are ignored next to '\$ref' in '/a:GET'",
            ),
            ignoredNextToRefWarnings(spec),
        )
    }

    @Test
    fun `3_1 schemas may annotate a reference`() {
        val spec = spec("openapi: 3.1.0\ninfo: {title: t, version: '1'}\ncomponents:\n  schemas:\n    A: {\$ref: '#/components/schemas/X', type: object}\n    X: {type: object}\n")
        assertEquals(emptyList(), ignoredNextToRefWarnings(spec))
    }

    @Test
    fun `nullable has no effect in 3_1`() {
        val spec = spec("openapi: 3.1.0\ninfo: {title: t, version: '1'}\ncomponents:\n  schemas:\n    A: {type: string, nullable: true}\n")
        assertEquals(
            listOf("'nullable' in 'A' has no effect since OpenAPI 3.1; add 'null' to its 'type' instead"),
            nullableIn31Warnings(spec),
        )
        assertEquals(emptyList(), nullableIn31Warnings(spec30("A: {type: string, nullable: true}")))
    }

    @Test
    fun `3_0 has no null type, but nullable is fine`() {
        val spec = spec30("""
            A: {type: 'null'}
            B: {type: string, nullable: true}
            C: {type: [string]}
        """)
        assertEquals(
            listOf(
                "type 'null' in 'A' is not one of array, boolean, integer, number, object, string",
            ),
            invalidTypeErrors(spec).filter { "'null'" in it },
        )
    }

    @Test
    fun `nullable compositions of 3_0 are walked as written`() {
        val spec = spec30("""
            A: {nullable: true, allOf: [{${'$'}ref: '#/components/schemas/X'}, {type: object}]}
            B: {nullable: true, oneOf: [{type: string}]}
            X: {type: object}
        """)
        val locations = mutableListOf<String>()
        spec.walkSchemas { locations += it.location }
        assertEquals(listOf("A", "A.allOf[0]", "A.allOf[1]", "B", "B.oneOf[0]", "X"), locations)
    }

    @Test
    fun `get and head with a body`() {
        val spec = spec(
            "openapi: 3.1.0\ninfo: {title: t, version: '1'}\n" + """
            paths:
              /a:
                get: {requestBody: {content: {application/json: {}}}, responses: {'200': {description: ok}}}
                head: {requestBody: {content: {application/json: {}}}, responses: {'200': {description: ok}}}
                post: {requestBody: {content: {application/json: {}}}, responses: {'200': {description: ok}}}
            """.trimIndent()
        )
        assertEquals(
            listOf(
                "operation '/a:GET' has a request body, which GET requests should not have",
                "operation '/a:HEAD' has a request body, which HEAD requests should not have",
            ),
            getWithBodyWarnings(spec),
        )
    }

    @Test
    fun `headers with underscores`() {
        val spec = spec(
            "openapi: 3.1.0\ninfo: {title: t, version: '1'}\n" + """
            paths:
              /a:
                get:
                  parameters:
                    - {name: X_Trace, in: header, schema: {type: string}}
                    - {name: page_size, in: query, schema: {type: integer}}
                  responses: {'200': {description: ok}}
            components:
              securitySchemes:
                key: {type: apiKey, in: header, name: api_key}
            """.trimIndent()
        )
        assertEquals(
            listOf(
                "header 'X_Trace' in '/a:GET' contains '_'; Apache and Nginx drop such headers by default",
                "header 'api_key' in 'components.securitySchemes.key' contains '_'; Apache and Nginx drop such headers by default",
            ),
            headerUnderscoreWarnings(spec),
        )
    }
}
