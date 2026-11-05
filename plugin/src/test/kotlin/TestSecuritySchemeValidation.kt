import de.quati.ogen.plugin.SpecsConfigBuilder
import de.quati.ogen.plugin.intern.tasks.Validator
import de.quati.ogen.plugin.intern.tasks.getUndefinedSecuritySchemeErrors
import org.gradle.api.GradleException
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TestSecuritySchemeValidation {
    companion object {
        private val logger = LoggerFactory.getLogger(TestSecuritySchemeValidation::class.java)!!

        private fun specConfig(spec: String) = SpecsConfigBuilder().apply {
            utilPackageName("de.quati.ogen.test.gen.util")
            add(packageName = "de.quati.ogen.test.gen") {
                specFile(TestSecuritySchemeValidation::class.java.getResource("$spec.yaml")!!.path)
                model {}
                validator {}
            }
        }.build().specs.single()

        private fun validate(spec: String) = Validator.validate(config = specConfig(spec), logger = logger)
    }

    @Test
    fun testUndefinedSecuritySchemes() {
        assertEquals(
            listOf(
                "security scheme 'apiKey' in top-level security is not defined in components.securitySchemes",
                "security scheme 'bearer' in '/pets:GET' is not defined in components.securitySchemes",
            ),
            getUndefinedSecuritySchemeErrors(specConfig("security-undefined").parseResult.openAPI),
        )
        val e = assertFailsWith<GradleException> { validate("security-undefined") }
        assertEquals("Found 2 undefined security scheme references.", e.message)
    }

    @Test
    fun testComponentsWithoutSecuritySchemes() {
        assertEquals(
            listOf("security scheme 'bearer' in '/pets:GET' is not defined in components.securitySchemes"),
            getUndefinedSecuritySchemeErrors(specConfig("security-no-schemes").parseResult.openAPI),
        )
        val e = assertFailsWith<GradleException> { validate("security-no-schemes") }
        assertEquals("Found 1 undefined security scheme references.", e.message)
    }

    @Test
    fun testDefinedSecuritySchemes() {
        validate("security-defined")
    }

    @Test
    fun testSecurityWithoutComponents() {
        // Checks the function alone: the naming convention check fails on a spec without components.
        assertEquals(
            emptyList(),
            getUndefinedSecuritySchemeErrors(specConfig("security-no-components").parseResult.openAPI),
        )
    }
}
