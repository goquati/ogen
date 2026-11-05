import de.quati.ogen.plugin.NameConvention
import de.quati.ogen.plugin.intern.tasks.Validator
import org.gradle.api.GradleException
import org.slf4j.event.Level
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TestValidator {
    @Test
    fun testUnparseableSpecFailsValidation() {
        assertValidationFails("invalid-yaml", "Validation failed.")
    }

    // validator-several-errors has a path parameter outside the template, a duplicated enum value and an undefined
    // required property, and a trailing slash as a warning.
    @Test
    fun testErrorsOfSeveralChecksFailTogether() {
        assertValidationFails("validator-several-errors", "Found 3 errors in the spec.")
    }

    @Test
    fun testNamingErrorsCountWithTheChecks() {
        assertValidationFails("validator-several-errors", "Found 4 errors in the spec.") {
            schemaNameFormat = NameConvention.CamelCase
        }
    }

    // Errors fail the build before warnings do, even with failOnWarnings; both are printed one per entry.
    @Test
    fun testErrorsAndWarningsAreLogged() {
        val logger = CapturingLogger()
        val e = assertFailsWith<GradleException> {
            Validator(logger).validate(validatorConfigs("validator-several-errors") { failOnWarnings = true })
        }
        assertEquals("Found 3 errors in the spec.", e.message)
        assertContains(logger.at(Level.WARN), "\tpath '/items/all/' has a trailing slash\n")
        assertContains(logger.at(Level.ERROR), "\tenum value 'open' in 'Item.state' is listed more than once\n")
    }

    @Test
    fun testSpecWithoutPathsOrComponents() {
        validate("validator-no-paths-3.1")
    }
}
