import de.quati.ogen.plugin.intern.validation.trailingSlashWarnings
import kotlin.test.Test

class TestTrailingSlashCheck {
    @Test
    fun testTrailingSlashes() {
        assertWarnings(
            "trailing-slash", ::trailingSlashWarnings, listOf(
                "path '/items/' has a trailing slash",
                "path '/items/{id}/' has a trailing slash",
                "path '/items//' has a trailing slash",
                "path '//' has a trailing slash",
            )
        )
    }

    @Test
    fun testRootPathAndPathsWithoutTrailingSlash() {
        assertPasses("no-trailing-slash", ::trailingSlashWarnings)
    }
}
