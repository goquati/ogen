import de.quati.ogen.plugin.intern.validation.successResponseWarnings
import kotlin.test.Test

class TestSuccessResponseCheck {
    @Test
    fun testSuccessResponseMissing() {
        assertWarnings(
            "success-response-missing", ::successResponseWarnings, listOf(
                "operation '/items:GET' has no 2xx or 3xx response",
                "operation '/items:POST' has no 2xx or 3xx response",
                "operation '/items/{id}:DELETE' has no 2xx or 3xx response",
            )
        )
    }

    // Unlike openapi-generator's recommendations, ogen's own checks don't depend on `recommendations`.
    @Test
    fun testSuccessResponseMissingWithoutRecommendations() {
        assertValidationFails("success-response-missing", warningsMessage("success-response-missing")) {
            recommendations = false
            failOnWarnings = true
        }
    }

    @Test
    fun testSuccessResponsePresent() {
        assertPasses("success-response-present", ::successResponseWarnings)
    }
}
