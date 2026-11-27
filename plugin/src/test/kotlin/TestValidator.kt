import de.quati.ogen.plugin.NameConvention
import de.quati.ogen.plugin.Severity
import de.quati.ogen.plugin.intern.tasks.Validator
import org.gradle.api.GradleException
import org.slf4j.event.Level
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TestValidator {
    @Test
    fun testUnparseableSpecFailsValidation() {
        assertValidationFails("invalid-yaml", failedMessage("invalid-yaml"))
    }

    // The helpers build the expected messages from the same config, so this pins the spec's file name itself.
    @Test
    fun testMessagesNameTheSpecFile() {
        val e = assertFailsWith<GradleException> { validate("invalid-yaml") }
        assertTrue(e.message!!.endsWith("/invalid-yaml.yaml' failed."), e.message)
    }

    // validator-several-errors has a path parameter outside the template, a duplicated enum value and an undefined
    // required property, and a trailing slash as a warning.
    @Test
    fun testErrorsOfSeveralChecksFailTogether() {
        assertValidationFails("validator-several-errors", errorsMessage("validator-several-errors", 3))
    }

    @Test
    fun testNamingErrorsCountWithTheChecks() {
        assertValidationFails("validator-several-errors", errorsMessage("validator-several-errors", 4)) {
            naming { schema = NameConvention.CamelCase }
            rules { namingConventions = Severity.Error }
        }
    }

    // Naming conventions are warnings by default: the schema name is reported, but only the three errors count.
    @Test
    fun testNamingConventionsAreWarningsByDefault() {
        val logger = CapturingLogger()
        val e = assertFailsWith<GradleException> {
            Validator(logger).validate(validatorConfigs("validator-several-errors") { naming { schema = NameConvention.CamelCase } })
        }
        assertEquals(errorsMessage("validator-several-errors", 3), e.message)
        assertTrue(logger.at(Level.WARN).any { "schema name 'Item'" in it }, logger.entries.toString())
    }

    // Errors and warnings are printed one per entry; only errors count.
    @Test
    fun testErrorsAndWarningsAreLogged() {
        val logger = CapturingLogger()
        val e = assertFailsWith<GradleException> {
            Validator(logger).validate(validatorConfigs("validator-several-errors"))
        }
        assertEquals(errorsMessage("validator-several-errors", 3), e.message)
        val spec = specName("validator-several-errors")
        assertContains(logger.at(Level.ERROR), "\nThe $spec is invalid.\nIssues:\n")
        assertContains(logger.at(Level.WARN), "\nThe $spec has issues or recommendations.\nIssues:\n")
        assertContains(logger.at(Level.WARN), "\tpath '/items/all/' has a trailing slash\n")
        assertContains(logger.at(Level.ERROR), "\tenum value 'open' in 'Item.state' is listed more than once\n")
    }

    @Test
    fun testSpecWithoutPathsOrComponents() {
        validate("validator-no-paths-3.1")
    }
}
