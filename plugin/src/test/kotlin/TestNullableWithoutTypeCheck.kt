import de.quati.ogen.plugin.intern.validation.nullableWithoutTypeWarnings
import kotlin.test.Test

class TestNullableWithoutTypeCheck {
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
                // A parameter of the Path Item is reported where it is declared.
                "/pets.parameters.X-Trace",
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
}
