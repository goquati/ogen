import de.quati.ogen.plugin.intern.tasks.Validator
import de.quati.ogen.plugin.intern.validation.brokenPathTemplateErrors
import org.gradle.api.GradleException
import org.slf4j.event.Level
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TestPathTemplateCheck {
    @Test
    fun testBrokenPathTemplates() {
        assertErrors(
            "path-template-broken", ::brokenPathTemplateErrors, listOf(
                "path '/items/{}' has an empty variable",
                "path '/orders/{id' has an unclosed '{'",
                "path '/users/id}' has a '}' without a '{'",
            )
        )
    }

    // swagger-parser already fails this spec, with a message about a parameter named '{id'; ogen's message is logged with it.
    @Test
    fun testNestedPathTemplate() {
        val logger = CapturingLogger()
        val e = assertFailsWith<GradleException> { Validator(logger).validate(validatorConfigs("path-template-nested")) }
        assertEquals(failedMessage("path-template-nested"), e.message)
        assertContains(logger.at(Level.ERROR).map { it.trim() }, "path '/carts/{{id}}' has a variable inside a variable")
    }

    @Test
    fun testValidPathTemplates() {
        assertPasses("path-template-valid", ::brokenPathTemplateErrors)
    }
}
