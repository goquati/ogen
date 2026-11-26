import de.quati.ogen.plugin.intern.validation.defaultOutsideEnumErrors
import kotlin.test.Test

class TestDefaultOutsideEnumCheck {
    @Test
    fun testDefaultsOutsideEnum() {
        assertErrors(
            "default-outside-enum", ::defaultOutsideEnumErrors, listOf(
                "default 'pending' in 'Pet.state' is not one of its enum values 'open', 'closed'",
                "default 3 in '/pets:GET.parameters.size' is not one of its enum values 1, 2",
            )
        )
        assertErrors(
            "default-outside-enum-3.1", ::defaultOutsideEnumErrors,
            listOf("default 'lots' in 'Level' is not one of its enum values 1, 2"),
        )
    }

    // In 3.0 a default that does not fit the type counts as none, as swagger-parser turned it into null.
    @Test
    fun testValidDefaults() {
        assertPasses("default-outside-enum-valid", ::defaultOutsideEnumErrors)
        assertPasses("default-outside-enum-valid-3.1", ::defaultOutsideEnumErrors)
    }
}
