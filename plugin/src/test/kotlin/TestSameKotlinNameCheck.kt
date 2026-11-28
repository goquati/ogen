import de.quati.ogen.plugin.intern.oas.OasParser
import de.quati.ogen.plugin.intern.oas.load.ResourceLoader
import de.quati.ogen.plugin.intern.validation.ValidatedSpec
import de.quati.ogen.plugin.intern.validation.sameKotlinNameWarnings
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals

class TestSameKotlinNameCheck {
    private fun spec(yaml: String): ValidatedSpec {
        val result = OasParser.parse(URI("mem:/spec.yaml"), ResourceLoader { ResourceLoader.Resource.Text(yaml, "spec.yaml") })
        return ValidatedSpec(result.openApi ?: error(result.errors.toString()), result.sourceVersion!!, result.bundle)
    }

    @Test
    fun `property names with the same Kotlin name`() {
        val spec = spec(
            """
            openapi: 3.1.0
            info: {title: t, version: '1'}
            paths: {}
            components:
              schemas:
                Thing:
                  type: object
                  required: [x_tenant]
                  properties:
                    xTenant: {type: string}
                    x-tenant: {type: string}
                    other: {type: object, properties: {a-b: {type: string}, aB: {type: string}}}
                  additionalProperties: {type: string}
                Distinct:
                  type: object
                  required: [id]
                  properties: {id: {type: string}, name: {type: string}}
                Union:
                  oneOf: [{${'$'}ref: '#/components/schemas/Variant'}]
                  discriminator: {propertyName: objectType}
                Variant:
                  type: object
                  properties: {objectType: {type: string}, object-type: {type: string}}
            """.trimIndent()
        )
        assertEquals(
            listOf(
                "schema 'Thing' has properties 'xTenant', 'x-tenant', 'x_tenant' with the same Kotlin name 'xTenant'; " +
                    "all but one get '_' appended",
                "schema 'Thing.other' has properties 'a-b', 'aB' with the same Kotlin name 'aB'; " +
                    "all but one get '_' appended",
            ),
            sameKotlinNameWarnings(spec),
        )
    }
}
