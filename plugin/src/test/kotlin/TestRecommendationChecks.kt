import de.quati.ogen.plugin.intern.oas.OasParser
import de.quati.ogen.plugin.intern.oas.load.ResourceLoader
import de.quati.ogen.plugin.intern.validation.ValidatedSpec
import de.quati.ogen.plugin.intern.validation.oneOfWithPropertiesWarnings
import de.quati.ogen.plugin.intern.validation.unsupportedOperationWarnings
import de.quati.ogen.plugin.intern.validation.unusedSchemaWarnings
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals

class TestRecommendationChecks {
    private fun spec(yaml: String): ValidatedSpec {
        val result = OasParser.parse(URI("mem:/spec.yaml"), ResourceLoader { ResourceLoader.Resource.Text(yaml, "spec.yaml") })
        return ValidatedSpec(result.openApi ?: error(result.errors.toString()), result.sourceVersion!!, result.bundle)
    }

    private val header = "openapi: 3.1.0\ninfo: {title: t, version: '1'}\n"

    @Test
    fun `schemas no operation uses`() {
        val spec = spec(
            header + """
            paths:
              /pets:
                get:
                  responses:
                    '200':
                      description: ok
                      content:
                        application/json:
                          schema: {${'$'}ref: '#/components/schemas/Pet'}
            components:
              schemas:
                Pet: {properties: {owner: {${'$'}ref: '#/components/schemas/Owner'}}}
                Owner: {type: object}
                Cat: {allOf: [{${'$'}ref: '#/components/schemas/Pet'}]}
                Orphan: {type: object}
                OrphanChild: {allOf: [{${'$'}ref: '#/components/schemas/Orphan'}]}
            """.trimIndent()
        )
        assertEquals(
            listOf("schema 'Orphan' is not used by any operation", "schema 'OrphanChild' is not used by any operation"),
            unusedSchemaWarnings(spec),
        )
    }

    // A kept schema counts as used, and so do the schemas it refers to; only `true`, also quoted, keeps one.
    @Test
    fun `schemas kept with x-ogen-keep`() {
        for (version in listOf("3.0.3", "3.1.0")) {
            val spec = spec(
                "openapi: $version\ninfo: {title: t, version: '1'}\npaths: {}\n" + """
                components:
                  schemas:
                    ProblemType: {type: string, enum: [not-found], x-ogen-keep: true}
                    Problem: {properties: {detail: {${'$'}ref: '#/components/schemas/Detail'}}, x-ogen-keep: true}
                    Detail: {type: object}
                    NotKept: {type: object, x-ogen-keep: false}
                    KeptAsString: {type: object, x-ogen-keep: "true"}
                """.trimIndent()
            )
            assertEquals(listOf("schema 'NotKept' is not used by any operation"), unusedSchemaWarnings(spec), version)
        }
    }

    // Every keyword that holds a schema counts, also below a kept schema.
    @Test
    fun `schemas referenced through any schema keyword`() {
        fun ref(name: String) = "{\$ref: '#/components/schemas/$name'}"
        val spec = spec(
            header + """
            paths:
              /tags:
                get:
                  responses:
                    '200':
                      description: ok
                      content:
                        application/json:
                          schema:
                            type: object
                            propertyNames: ${ref("TagKey")}
                            if: ${ref("If")}
                            then: ${ref("Then")}
                            else: ${ref("Else")}
                            dependentSchemas: {a: ${ref("Dependent")}}
                            unevaluatedProperties: ${ref("UnevaluatedProperties")}
            components:
              schemas:
                TagKey: {type: string}
                If: {type: object}
                Then: {type: object}
                Else: {type: object}
                Dependent: {type: object}
                UnevaluatedProperties: {type: string}
                Kept: {type: array, contains: ${ref("Contains")}, unevaluatedItems: ${ref("UnevaluatedItems")}, x-ogen-keep: true}
                Contains: {type: string}
                UnevaluatedItems: {type: string}
                Orphan: {type: object}
            """.trimIndent()
        )
        assertEquals(listOf("schema 'Orphan' is not used by any operation"), unusedSchemaWarnings(spec))
    }

    @Test
    fun `properties next to oneOf`() {
        val spec = spec(
            header + """
            components:
              schemas:
                Mixed: {properties: {a: {type: string}}, oneOf: [{type: object}]}
                WithDiscriminatorProperty: {properties: {discriminator: {type: string}}, oneOf: [{type: object}]}
                AnyOf: {properties: {a: {type: string}}, anyOf: [{type: object}]}
            """.trimIndent()
        )
        assertEquals(
            listOf("schema 'Mixed' defines properties next to oneOf, which other tools may read differently"),
            oneOfWithPropertiesWarnings(spec),
        )
    }

    @Test
    fun `operations ogen does not generate`() {
        val spec = spec(
            "openapi: 3.2.0\ninfo: {title: t, version: '1'}\n" + """
            paths:
              /search:
                query: {responses: {'200': {description: ok}}}
                additionalOperations:
                  LINK: {responses: {'200': {description: ok}}}
            """.trimIndent()
        )
        assertEquals(
            listOf(
                "operation '/search:QUERY' is not generated: ogen does not support the QUERY method",
                "operation '/search:LINK' is not generated: ogen does not support additionalOperations",
            ),
            unsupportedOperationWarnings(spec),
        )
    }
}
