import de.quati.ogen.plugin.intern.validation.nullableWithoutTypeWarnings
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.SpecVersion
import io.swagger.v3.oas.models.media.Schema
import kotlin.test.Test
import kotlin.test.assertEquals

class TestNullableWithoutTypeCheck {
    companion object {
        // Built by hand: the parser drops `nullable` in 3.1 and next to a `$ref` in 3.0, so no YAML reaches these cases.
        private fun specWithSchema(version: SpecVersion, schema: Schema<*>) =
            OpenAPI().specVersion(version).components(Components().addSchemas("Pet", schema))
    }

    @Test
    fun testNullableWithoutType() {
        assertWarnings(
            "nullable-without-type", ::nullableWithoutTypeWarnings, listOf(
                "Pet.name",
                "Pet.labels.additionalProperties",
                "Pet.other.not",
                "Dog.allOf[1].breed",
                "components.parameters.Limit",
                "components.requestBodies.NewPet.application/json",
                "components.responses.Pets.application/json",
                "components.headers.Location",
                "/pets:GET.parameters.X-Trace",
                "/pets:POST.parameters.filter.application/json",
                "/pets:POST.requestBody.application/json.name",
                "/pets:POST.responses.200.application/json[]",
                "/pets:POST.responses.200.headers.X-Rate",
            ).map {
                "'nullable: true' without a 'type' in '$it': it only takes effect next to a 'type'; " +
                    "a nullable \$ref goes in an 'allOf'"
            }
        )
    }

    @Test
    fun testNullableWithTypeOrComposition() {
        assertPasses("nullable-without-type-valid", ::nullableWithoutTypeWarnings)
    }

    @Test
    fun testNullableWithoutTypeWithRef() {
        val schema = Schema<Any>().nullable(true).also { it.`$ref` = "#/components/schemas/Owner" }
        assertEquals(emptyList(), nullableWithoutTypeWarnings(specWithSchema(SpecVersion.V30, schema)))
    }

    @Test
    fun testNullableWithoutTypeIgnoredInOpenApi31() {
        val schema = Schema<Any>().nullable(true)
        assertEquals(1, nullableWithoutTypeWarnings(specWithSchema(SpecVersion.V30, schema)).size)
        assertEquals(emptyList(), nullableWithoutTypeWarnings(specWithSchema(SpecVersion.V31, schema)))
    }
}
