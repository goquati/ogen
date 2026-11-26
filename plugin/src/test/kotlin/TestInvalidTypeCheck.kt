import de.quati.ogen.plugin.intern.validation.invalidTypeErrors
import kotlin.test.Test

class TestInvalidTypeCheck {
    @Test
    fun testInvalidTypes() {
        assertErrors(
            "invalid-type", ::invalidTypeErrors, listOf(
                "type 'integr' in 'Pet.count' is not one of array, boolean, integer, number, object, string",
                "type 'strng' in 'Pet.tags[]' is not one of array, boolean, integer, number, object, string",
            )
        )
        assertErrors(
            "invalid-type-3.1", ::invalidTypeErrors,
            listOf("type 'integr' in 'Pet.count' is not one of array, boolean, integer, null, number, object, string"),
        )
    }

    // An invalid entry in a 3.1 type list is reported like a single invalid type.
    @Test
    fun testInvalidTypeList() {
        assertErrors(
            "invalid-type-list-3.1", ::invalidTypeErrors,
            listOf("type 'nul' in 'Pet.name' is not one of array, boolean, integer, null, number, object, string"),
        )
    }

    @Test
    fun testValidTypes() {
        assertPasses("invalid-type-valid-3.1", ::invalidTypeErrors)
    }
}
