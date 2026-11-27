import de.quati.ogen.plugin.RuleSet
import de.quati.ogen.plugin.Severity
import de.quati.ogen.plugin.ValidatorRule
import de.quati.ogen.plugin.intern.tasks.Validator
import org.gradle.api.GradleException
import org.slf4j.event.Level
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The recommendations that replace two of openapi-generator's rules, as the validator logs them. */
class TestValidatorWarningDetails {
    // validator-warnings has invalid types on purpose; they fail validation, after every warning is logged.
    private fun warnings(spec: String, rules: Map<ValidatorRule, Severity> = RuleSet.Default): List<String> {
        val logger = CapturingLogger()
        val e = assertFailsWith<GradleException> {
            Validator(logger = logger).validate(configs = validatorConfigs(spec) { rules(rules) })
        }
        assertEquals(errorsMessage(spec, 2), e.message)
        return logger.at(Level.WARN)
    }

    @Test
    fun testUnusedSchema() {
        assertContains(warnings("validator-warnings"), "\tschema 'Orphan' is not used by any operation\n")
    }

    @Test
    fun testGetWithBody() {
        assertContains(
            warnings("validator-warnings"),
            "\toperation '/pets:GET' has a request body, which GET requests should not have\n",
        )
    }

    // RuleSet.Lenient turns off recommendations, but not other warnings such as unused schemas.
    @Test
    fun testLenientSetTurnsOffRecommendations() {
        val warnings = warnings("validator-warnings", RuleSet.Lenient)
        assertEquals(emptyList(), warnings.filter { "request body" in it })
        assertContains(warnings, "\tschema 'Orphan' is not used by any operation\n")
    }
}
