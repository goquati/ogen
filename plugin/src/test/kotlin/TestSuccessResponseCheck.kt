import de.quati.ogen.plugin.intern.validation.successResponseWarnings
import de.quati.ogen.plugin.RuleSet
import de.quati.ogen.plugin.Severity
import de.quati.ogen.plugin.ValidatorRule
import kotlin.test.Test
import kotlin.test.assertEquals

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

    // Not a recommendation: RuleSet.Lenient keeps it a warning, and as an error it fails the build.
    @Test
    fun testSuccessResponseMissingInLenientSet() {
        val spec = "success-response-missing"
        assertEquals(Severity.Warning, RuleSet.Lenient[ValidatorRule.MissingSuccessResponses])
        assertValidationFails(spec, errorsMessage(spec, 3)) {
            rules(RuleSet.Lenient) { missingSuccessResponses = Severity.Error }
        }
    }

    @Test
    fun testSuccessResponsePresent() {
        assertPasses("success-response-present", ::successResponseWarnings)
    }
}
