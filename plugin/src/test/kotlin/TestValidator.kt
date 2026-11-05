import de.quati.ogen.plugin.SpecsConfigBuilder
import de.quati.ogen.plugin.intern.tasks.Validator
import de.quati.ogen.plugin.intern.tasks.addSuccessResponseWarnings
import io.swagger.parser.OpenAPIParser
import org.gradle.api.GradleException
import org.openapitools.codegen.validation.ValidationResult
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TestValidator {
    companion object {
        private val logger = LoggerFactory.getLogger(TestValidator::class.java)!!

        private fun specPath(spec: String) = TestValidator::class.java.getResource("$spec.yaml")!!.path

        private fun validate(spec: String, recommendations: Boolean = true) {
            val configs = SpecsConfigBuilder().apply {
                utilPackageName("de.quati.ogen.test.gen.util")
                add(packageName = "de.quati.ogen.test.gen") {
                    specFile(specPath(spec))
                    model {}
                    validator {
                        failOnWarnings = true
                        this.recommendations = recommendations
                    }
                }
            }.build()
            Validator(logger = logger).validate(configs = configs)
        }

        private fun successResponseWarnings(spec: String): List<String> {
            val openApi = OpenAPIParser().readLocation(specPath(spec), null, null).openAPI
            return ValidationResult().apply { addSuccessResponseWarnings(openApi) }.warnings.map { it.message }
        }
    }

    @Test
    fun testSuccessResponseMissing() {
        assertEquals(
            listOf(
                "operation '/items:GET' has no 2xx or 3xx response",
                "operation '/items:POST' has no 2xx or 3xx response",
                "operation '/items/{id}:DELETE' has no 2xx or 3xx response",
            ),
            successResponseWarnings("success-response-missing"),
        )
        val e = assertFailsWith<GradleException> { validate("success-response-missing") }
        assertEquals("Validation failed due to warnings (failOnWarnings = true).", e.message)
    }

    @Test
    fun testSuccessResponsePresent() {
        assertEquals(emptyList(), successResponseWarnings("success-response-present"))
        validate("success-response-present")
    }

    @Test
    fun testSuccessResponseWithoutRecommendations() {
        validate("success-response-missing", recommendations = false)
    }

    @Test
    fun testSuccessResponseUnparseableSpec() {
        val e = assertFailsWith<GradleException> { validate("success-response-unparseable") }
        assertEquals("Validation failed.", e.message)
    }
}
