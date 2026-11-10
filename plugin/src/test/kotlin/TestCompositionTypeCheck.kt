import de.quati.ogen.plugin.intern.validation.compositionTypeErrors
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCompositionTypeCheck {
    companion object {
        private fun ref(target: String) = Schema<Any>().also { it.`$ref` = target }
        private fun stringWithAllOf(vararg members: Schema<*>) = Schema<Any>().type("string").allOf(members.toList())
    }

    @Test
    fun testConflictingCompositionTypes() {
        assertErrors(
            "composition-types", ::compositionTypeErrors, listOf(
                "allOf members 0 (string) and 1 (integer) in 'Mixed' have no type in common",
                "allOf members 0 (string) and 1 (integer) in 'Three' have no type in common",
                "allOf members 0 (string) and 2 (boolean) in 'Three' have no type in common",
                "allOf members 1 (integer) and 2 (boolean) in 'Three' have no type in common",
                "type string in 'Pet' has no type in common with its allOf member 0 (object, from Owner)",
                "type object in 'Choice' has no type in common with any of its oneOf members",
                "type boolean in 'Either' has no type in common with any of its anyOf members",
                // Null is shared only when both sides are nullable.
                "allOf members 0 (null or string) and 1 (integer) in 'NotNullable' have no type in common",
                // The label names the reference written in the member, followed to the type through every alias.
                "type string in 'Chained' has no type in common with its allOf member 0 (integer, from AliasOfAlias)",
                "type null or string in 'NullOnOneSide' has no type in common with any of its anyOf members",
                "allOf members 0 (object, from Owner) and 1 (array) in 'Wrapper.owner' have no type in common",
                "allOf members 0 (string) and 1 (boolean) in '/pets:GET.parameters.filter' have no type in common",
            ),
            parse = ::declaredTypesSpec,
        )
    }

    @Test
    fun testConflictingCompositionTypesInOpenApi31() {
        assertErrors(
            "composition-types-3.1", ::compositionTypeErrors, listOf(
                "allOf members 0 (null or string, from Label) and 1 (integer) in 'Mixed' have no type in common",
                "type null in 'Nothing' has no type in common with any of its oneOf members",
                "type string in 'NoneOfAny' has no type in common with any of its anyOf members",
                "type string in 'RefWithType' has no type in common with its allOf member 0 (integer)",
                "type object in 'Pair[0]' has no type in common with its allOf member 0 (string)",
            ),
            parse = ::declaredTypesSpec,
        )
    }

    @Test
    fun testFittingCompositionTypes() {
        assertPasses("composition-types-valid", ::compositionTypeErrors, parse = ::declaredTypesSpec)
        assertPasses("composition-types-valid-3.1", ::compositionTypeErrors, parse = ::declaredTypesSpec)
    }

    // Built by hand: swagger-parser reports both references before ogen's checks run.
    @Test
    fun testUnresolvableReferencesCountAsUntyped() {
        val spec = OpenAPI().components(
            Components()
                .addSchemas("A", ref("#/components/schemas/B"))
                .addSchemas("B", ref("#/components/schemas/A"))
                .addSchemas("Cycle", stringWithAllOf(ref("#/components/schemas/A"), Schema<Any>().type("integer")))
                .addSchemas("External", stringWithAllOf(ref("other.yaml#/components/schemas/Count")))
        )
        assertEquals(
            listOf("type string in 'Cycle' has no type in common with its allOf member 1 (integer)"),
            compositionTypeErrors(spec),
        )
    }
}
