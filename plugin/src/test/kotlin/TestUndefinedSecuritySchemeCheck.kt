import de.quati.ogen.plugin.intern.validation.undefinedSecuritySchemeErrors
import kotlin.test.Test

class TestUndefinedSecuritySchemeCheck {
    @Test
    fun testUndefinedSecuritySchemes() {
        assertErrors(
            "security-undefined", ::undefinedSecuritySchemeErrors, listOf(
                "security scheme 'apiKey' in the top-level security is not defined in components.securitySchemes",
                "security scheme 'bearer' in '/pets:GET' is not defined in components.securitySchemes",
            )
        )
    }

    @Test
    fun testComponentsWithoutSecuritySchemes() {
        assertErrors(
            "security-no-schemes", ::undefinedSecuritySchemeErrors,
            listOf("security scheme 'bearer' in '/pets:GET' is not defined in components.securitySchemes"),
        )
    }

    // An empty requirement `{}` and `security: []` are valid.
    @Test
    fun testDefinedSecuritySchemes() {
        assertPasses("security-defined", ::undefinedSecuritySchemeErrors)
        assertPasses("security-no-components", ::undefinedSecuritySchemeErrors)
    }
}
