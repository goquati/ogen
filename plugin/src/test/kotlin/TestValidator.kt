import de.quati.ogen.plugin.NameConvention
import de.quati.ogen.plugin.SpecsConfigBuilder
import de.quati.ogen.plugin.intern.model.config.SpecConfigs
import de.quati.ogen.plugin.intern.tasks.Validator
import de.quati.ogen.plugin.intern.tasks.getPathParameterErrors
import org.gradle.api.GradleException
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TestValidator {
    companion object {
        private val logger = LoggerFactory.getLogger(TestValidator::class.java)!!

        // Every name format is Any, so only the check under test can fail.
        private fun configs(spec: String): SpecConfigs {
            val group = "de.quati.ogen.test"
            return SpecsConfigBuilder().apply {
                utilPackageName("$group.gen.util")
                add(packageName = "$group.gen") {
                    specFile(TestValidator::class.java.getResource("$spec.yaml")!!.path)
                    model {}
                    validator {
                        tagFormat = NameConvention.Any
                        operationIdFormat = NameConvention.Any
                        propertyNameFormat = NameConvention.Any
                        schemaNameFormat = NameConvention.Any
                        pathSegmentFormat = NameConvention.Any
                        parameterFormat {
                            path = NameConvention.Any
                            query = NameConvention.Any
                        }
                    }
                }
            }.build()
        }

        private fun SpecConfigs.validate() = Validator(logger = logger).validate(configs = this)

        private fun assertPathParameterErrors(spec: String, expected: List<String>) {
            val configs = configs(spec)
            assertEquals(expected, getPathParameterErrors(configs.specs.single().parseResult.openAPI))
            val e = assertFailsWith<GradleException> { configs.validate() }
            assertEquals("Found ${expected.size} path parameter errors.", e.message)
        }
    }

    @Test
    fun testPathParamsInTemplate() {
        configs("path-params-in-template").validate()
    }

    @Test
    fun testPathParamsInTemplate31() {
        configs("path-params-in-template-31").validate()
    }

    // OpenAPI 3.0: swagger-parser copies path-level parameters into each operation.
    @Test
    fun testPathParamsNotInTemplate() {
        assertPathParameterErrors(
            spec = "path-params-not-in-template",
            expected = listOf(
                "path parameter 'filterId' in '/items/all:GET' has no '{filterId}' in the path",
                "path parameter 'itemId' in '/items/all:GET' has no '{itemId}' in the path",
                "path parameter 'filterId' in '/items/all:DELETE' has no '{filterId}' in the path",
                "path parameter 'ItemId' in '/items/{itemId}:GET' has no '{ItemId}' in the path",
                "path parameter 'ownerId' in '/items/{itemId}:GET' has no '{ownerId}' in the path",
            ),
        )
    }

    // OpenAPI 3.1: path-level parameters stay on the path item.
    @Test
    fun testPathParamsNotInTemplate31() {
        assertPathParameterErrors(
            spec = "path-params-not-in-template-31",
            expected = listOf(
                "path parameter 'filterId' in '/items/all' (path level) has no '{filterId}' in the path",
                "path parameter 'itemId' in '/items/all:GET' has no '{itemId}' in the path",
                "path parameter 'ItemId' in '/items/{itemId}:GET' has no '{ItemId}' in the path",
                "path parameter 'ownerId' in '/items/{itemId}:GET' has no '{ownerId}' in the path",
            ),
        )
    }
}
