import de.quati.ogen.plugin.intern.oas.Diagnostic
import kotlin.test.Test
import kotlin.test.assertEquals

/** `nullable: true` without a type is found by the migration of 3.0 specs; the validator reports its warning. */
class TestNullableWithoutTypeCheck {
    private fun nullableWarnings(spec: String) = validatorConfigs(spec).specs.single().oasParseResult.diagnostics
        .filter { it.severity == Diagnostic.Severity.WARNING && "'nullable: true' without a 'type'" in it.message }
        .map { it.location!!.pointer.toString() }

    @Test
    fun testNullableWithoutType() {
        assertEquals(
            listOf(
                "/paths/~1pets/parameters/0/schema/nullable",
                "/paths/~1pets/post/parameters/1/content/application~1json/schema/nullable",
                "/paths/~1pets/post/requestBody/content/application~1json/schema/properties/name/nullable",
                "/paths/~1pets/post/responses/200/headers/X-Rate/schema/nullable",
                "/paths/~1pets/post/responses/200/content/application~1json/schema/items/nullable",
                "/components/parameters/Limit/schema/nullable",
                "/components/requestBodies/NewPet/content/application~1json/schema/nullable",
                "/components/responses/Pets/content/application~1json/schema/nullable",
                "/components/headers/Location/schema/nullable",
                "/components/schemas/Pet/properties/name/nullable",
                "/components/schemas/Pet/properties/labels/additionalProperties/nullable",
                "/components/schemas/Pet/properties/other/not/nullable",
                "/components/schemas/Dog/allOf/1/properties/breed/nullable",
            ),
            nullableWarnings("nullable-without-type"),
        )
        validate("nullable-without-type")
        assertValidationFails("nullable-without-type", warningsMessage("nullable-without-type")) { failOnWarnings = true }
    }

    @Test
    fun testNullableWithTypeOrComposition() {
        assertEquals(emptyList(), nullableWarnings("nullable-without-type-valid"))
        validate("nullable-without-type-valid")
    }
}
