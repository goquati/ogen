import de.quati.ogen.plugin.NameConvention
import de.quati.ogen.plugin.intern.tasks.Validator
import kotlin.test.Test
import kotlin.test.assertEquals

class TestNamingConventions {
    // The root path, a trailing slash and a double slash leave an empty segment, which the convention must not reject.
    @Test
    fun testPathSegments() {
        val spec = validatorConfigs("naming-path-segments") { pathSegmentFormat = NameConvention.KebabCase }.specs.single()
        assertEquals(
            listOf("path segment 'Items' in '/Items' matches not path segment format kebab-case"),
            Validator.getNamingConventionErrors(spec.parseResult.openAPI, spec.validatorConfig!!),
        )
        assertValidationFails("naming-path-segments", "Found 1 errors in the spec.") {
            pathSegmentFormat = NameConvention.KebabCase
        }
    }
}
