import kotlin.test.Test

class TestValidator {
    @Test
    fun testUnparseableSpecFailsValidation() {
        assertValidationFails("invalid-yaml", "Validation failed.")
    }
}
