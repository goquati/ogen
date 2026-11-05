import de.quati.ogen.plugin.intern.validation.patternWarnings
import kotlin.test.Test

class TestPatternCheck {
    @Test
    fun testInvalidPatterns() {
        assertWarnings(
            "pattern-invalid", ::patternWarnings, listOf(
                "pattern '[' in 'Item.name' does not compile as a Java regex: Unclosed character class",
                // Valid in JavaScript, where it means any character; Java rejects it, hence a warning only.
                "pattern '[^]' in 'Item.anything' does not compile as a Java regex: Unclosed character class",
                "pattern '([a-z]' in '/items:GET.parameters.code' does not compile as a Java regex: Unclosed group",
            )
        )
    }

    @Test
    fun testInvalidPatternPropertiesKey() {
        assertWarnings(
            "pattern-invalid-3.1", ::patternWarnings,
            listOf("patternProperties key '^x-[' in 'Labels' does not compile as a Java regex: Unclosed character class"),
        )
    }

    @Test
    fun testValidPatterns() {
        assertPasses("pattern-valid", ::patternWarnings)
    }
}
