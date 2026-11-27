import de.quati.ogen.plugin.intern.validation.compositionTypeErrors
import de.quati.ogen.plugin.intern.oas.OasVersion
import de.quati.ogen.plugin.intern.oas.model.Components
import de.quati.ogen.plugin.intern.oas.model.Info
import de.quati.ogen.plugin.intern.oas.model.OpenApi
import de.quati.ogen.plugin.intern.oas.model.Schema
import de.quati.ogen.plugin.intern.validation.ValidatedSpec
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCompositionTypeCheck {

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

    // Built by hand: the parser reports both references before ogen's checks run.
    @Test
    fun testUnresolvableReferencesCountAsUntyped() {
        fun ref(target: String) = Schema.Obj(ref = target)
        fun stringWithAllOf(vararg members: Schema) = Schema.Obj(type = listOf("string"), allOf = members.toList())
        val spec = ValidatedSpec(
            OpenApi(
                openapi = "3.1.0",
                info = Info(title = "t", version = "1"),
                components = Components(
                    schemas = mapOf(
                        "A" to ref("#/components/schemas/B"),
                        "B" to ref("#/components/schemas/A"),
                        "Cycle" to stringWithAllOf(ref("#/components/schemas/A"), Schema.Obj(type = listOf("integer"))),
                        "External" to stringWithAllOf(ref("other.yaml#/components/schemas/Count")),
                    )
                ),
            ),
            OasVersion.V3_1,
        )
        assertEquals(
            listOf("type string in 'Cycle' has no type in common with its allOf member 1 (integer)"),
            compositionTypeErrors(spec),
        )
    }
}
