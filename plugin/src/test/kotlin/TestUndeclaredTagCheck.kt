import de.quati.ogen.plugin.intern.validation.undeclaredTagWarnings
import kotlin.test.Test

class TestUndeclaredTagCheck {
    @Test
    fun testUndeclaredTags() {
        assertWarnings(
            "undeclared-tags", ::undeclaredTagWarnings, listOf(
                "tag 'other' in '/items:POST' is not declared in the top-level tags",
                "tag 'itmes' in '/items/{itemId}:GET' is not declared in the top-level tags",
                "tag 'other' in '/items/{itemId}:GET' is not declared in the top-level tags",
                "tag 'itemdetails' in '/items/{itemId}:DELETE' is not declared in the top-level tags",
            )
        )
    }

    @Test
    fun testDeclaredTags() {
        assertPasses("declared-tags", ::undeclaredTagWarnings)
    }

    // A spec that declares no top-level tags opts out.
    @Test
    fun testNoDeclaredTags() {
        assertPasses("no-declared-tags", ::undeclaredTagWarnings)
    }
}
