import de.quati.ogen.plugin.SpecsConfigBuilder
import de.quati.ogen.plugin.intern.model.config.SpecConfig
import de.quati.ogen.plugin.intern.tasks.Validator
import de.quati.ogen.plugin.intern.tasks.addUndeclaredTagWarnings
import org.gradle.api.GradleException
import org.openapitools.codegen.validation.ValidationResult
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TestUndeclaredTags {
    companion object {
        private val logger = LoggerFactory.getLogger(TestUndeclaredTags::class.java)!!

        private fun specConfig(spec: String): SpecConfig = SpecsConfigBuilder().apply {
            utilPackageName("de.quati.ogen.test.gen.util")
            add(packageName = "de.quati.ogen.test.gen") {
                specFile(TestUndeclaredTags::class.java.getResource("$spec.yaml")!!.path)
                validator { failOnWarnings = true }
                model {}
            }
        }.build().specs.single()

        private fun undeclaredTagWarnings(spec: String): List<String> = ValidationResult()
            .apply { addUndeclaredTagWarnings(specConfig(spec).parseResult.openAPI) }
            .warnings.map { it.message }
    }

    @Test
    fun testUndeclaredTags() {
        assertEquals(
            listOf(
                "tag 'other' in '/items:POST' is not declared in the top-level tags",
                "tag 'itmes' in '/items/{itemId}:GET' is not declared in the top-level tags",
                "tag 'other' in '/items/{itemId}:GET' is not declared in the top-level tags",
                "tag 'itemdetails' in '/items/{itemId}:DELETE' is not declared in the top-level tags",
            ),
            undeclaredTagWarnings("undeclared-tags"),
        )
        val e = assertFailsWith<GradleException> {
            Validator.validate(config = specConfig("undeclared-tags"), logger = logger)
        }
        assertEquals("Validation failed due to warnings (failOnWarnings = true).", e.message)
    }

    @Test
    fun testDeclaredTags() {
        Validator.validate(config = specConfig("declared-tags"), logger = logger)
    }

    @Test
    fun testNoDeclaredTags() {
        Validator.validate(config = specConfig("no-declared-tags"), logger = logger)
    }
}
