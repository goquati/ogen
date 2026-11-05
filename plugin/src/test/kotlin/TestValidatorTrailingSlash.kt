import de.quati.ogen.plugin.NameConvention
import de.quati.ogen.plugin.SpecsConfigBuilder
import de.quati.ogen.plugin.intern.model.config.SpecConfigs
import de.quati.ogen.plugin.intern.tasks.Validator
import de.quati.ogen.plugin.intern.tasks.addTrailingSlashWarnings
import org.gradle.api.GradleException
import org.openapitools.codegen.validation.ValidationResult
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TestValidatorTrailingSlash {
    companion object {
        private val logger = LoggerFactory.getLogger(TestValidatorTrailingSlash::class.java)!!

        private fun configs(spec: String): SpecConfigs = SpecsConfigBuilder().apply {
            utilPackageName("de.quati.ogen.test.gen.util")
            add(packageName = "de.quati.ogen.test.gen") {
                specFile(TestValidatorTrailingSlash::class.java.getResource("$spec.yaml")!!.path)
                model {}
                validator {
                    failOnWarnings = true
                    recommendations = false
                    // '/' and '/items/' split into an empty segment, which the naming check would reject.
                    pathSegmentFormat = NameConvention.Any
                }
            }
        }.build()

        private fun trailingSlashWarnings(spec: String): List<String> {
            val openApi = configs(spec).specs.single().parseResult.openAPI
            return ValidationResult().apply { addTrailingSlashWarnings(openApi) }.warnings.map { it.message }
        }
    }

    @Test
    fun testWarnsAboutTrailingSlashes() {
        assertEquals(
            listOf(
                "Path '/items/' has a trailing slash.",
                "Path '/items/{id}/' has a trailing slash.",
                "Path '/items//' has a trailing slash.",
                "Path '//' has a trailing slash.",
            ),
            trailingSlashWarnings("validator-trailing-slash"),
        )
    }

    @Test
    fun testTrailingSlashFailsWithFailOnWarnings() {
        val e = assertFailsWith<GradleException> {
            Validator(logger).validate(configs("validator-trailing-slash"))
        }
        assertEquals("Validation failed due to warnings (failOnWarnings = true).", e.message)
    }

    @Test
    fun testRootPathAndPathsWithoutTrailingSlashPass() {
        assertEquals(emptyList(), trailingSlashWarnings("validator-no-trailing-slash"))
        Validator(logger).validate(configs("validator-no-trailing-slash"))
    }
}
