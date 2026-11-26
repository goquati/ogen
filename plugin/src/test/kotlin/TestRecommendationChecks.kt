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
