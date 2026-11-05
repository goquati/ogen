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

    // swagger-parser rejects an invalid entry in a 3.1 type list itself, before this check runs.
    @Test
    fun testInvalidTypeListIsAParserError() {
        assertValidationFails("invalid-type-list-3.1", failedMessage("invalid-type-list-3.1"))
    }

    @Test
    fun testValidTypes() {
        assertPasses("invalid-type-valid-3.1", ::invalidTypeErrors)
    }
}
