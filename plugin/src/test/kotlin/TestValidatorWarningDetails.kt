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
    private fun warnings(spec: String, recommendations: Boolean = true): List<String> {
        val logger = CapturingLogger()
        val e = assertFailsWith<GradleException> {
            Validator(logger = logger).validate(configs = validatorConfigs(spec) { this.recommendations = recommendations })
        }
        assertEquals(errorsMessage(spec, 2), e.message)
        return logger.at(Level.WARN)
    }

    @Test
    fun testUnusedSchema() {
        assertContains(warnings("validator-warnings"), "\tschema 'Orphan' is not used by any operation\n")
    }

    @Test
    fun testRecommendationsCanBeDisabled() {
        val warnings = warnings("validator-warnings", recommendations = false)
        assertEquals(emptyList(), warnings.filter { "is not used by any operation" in it })
    }
}
