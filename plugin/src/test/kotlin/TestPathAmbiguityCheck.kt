import de.quati.ogen.plugin.intern.validation.ambiguousPathWarnings
import de.quati.ogen.plugin.intern.validation.identicalPathErrors
import kotlin.test.Test

class TestPathAmbiguityCheck {
    @Test
    fun testIdenticalPaths() {
        assertErrors(
            "path-identical", ::identicalPathErrors, listOf(
                "path '/items/{id}' is identical to '/items/{itemId}': only the variable names differ",
                "path '/items/{key}' is identical to '/items/{itemId}': only the variable names differ",
            )
        )
    }

    @Test
    fun testAmbiguousPaths() {
        assertWarnings(
            "path-ambiguous", ::ambiguousPathWarnings,
            listOf("paths '/{entity}/me' and '/books/{id}' are ambiguous: GET/PUT /books/me matches both"),
        )
    }

    // `/items/all` wins over `/items/{id}` although it comes second, the crossing pair shares no method, and
    // `/items/` is another path than `/items`.
    @Test
    fun testUnambiguousPaths() {
        assertPasses("path-unambiguous", ::ambiguousPathWarnings)
        assertPasses("path-unambiguous", ::identicalPathErrors)
    }
}
