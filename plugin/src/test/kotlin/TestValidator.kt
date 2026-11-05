import de.quati.ogen.plugin.SpecsConfigBuilder
import de.quati.ogen.plugin.intern.model.config.SpecConfigs
import de.quati.ogen.plugin.intern.tasks.Validator
import de.quati.ogen.plugin.intern.tasks.getUndefinedRequiredPropertyErrors
import org.gradle.api.GradleException
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TestValidator {
    companion object {
        private val logger = LoggerFactory.getLogger(TestValidator::class.java)!!

        private fun specConfigs(spec: String): SpecConfigs = SpecsConfigBuilder().apply {
            utilPackageName("de.quati.ogen.test.gen.util")
            add(packageName = "de.quati.ogen.test.gen") {
                specFile(TestValidator::class.java.getResource("$spec.yaml")!!.path)
                model {}
                validator {}
            }
        }.build()

        private fun undefinedRequiredPropertyErrors(spec: String) =
            getUndefinedRequiredPropertyErrors(specConfigs(spec).specs.single().parseResult.openAPI)

        private fun validate(spec: String) = Validator(logger = logger).validate(configs = specConfigs(spec))
    }

    @Test
    fun testRequiredPropertiesUndefined() {
        assertEquals(
            listOf(
                "required property 'colour' in Item is not defined in its properties",
                "required property 'street' in Item.address is not defined in its properties",
                "required property 'label' in Item.tags[] is not defined in its properties",
                "required property 'code' in Closed is not defined in its properties",
                "required property 'key' in Dictionary.additionalProperties is not defined in its properties",
                "required property 'age' in Extended.allOf[1] is not defined in its properties",
                "required property 'nickanme' in TightenedTypo is not defined in its properties",
                "required property 'nickanme' in RequiresNickname is not defined in its properties",
                "required property 'nmae' in EitherName.oneOf[1] is not defined in its properties",
                "required property 'nickname' in AnyName.anyOf[0] is not defined in its properties",
                "required property 'cell' in Grid.allOf[0].rows[][] is not defined in its properties",
                "required property 'size' in parameter:Paging is not defined in its properties",
                "required property 'label' in requestBody:NewItem:application/json is not defined in its properties",
                "required property 'reason' in response:NotFound:application/json is not defined in its properties",
                "required property 'region' in /items:POST.scope is not defined in its properties",
                "required property 'kind' in /items:POST.filter is not defined in its properties",
                "required property 'field' in /items:POST.sort:application/json is not defined in its properties",
                "required property 'title' in /items:POST:requestBody:application/json is not defined in its properties",
                "required property 'identifier' in /items:POST:response:201:application/json is not defined in its properties",
                "required property 'traceId' in /items:POST:response:201:header:X-Trace is not defined in its properties",
            ),
            undefinedRequiredPropertyErrors("required-properties-undefined"),
        )
        val e = assertFailsWith<GradleException> { validate("required-properties-undefined") }
        assertEquals("Found 20 required properties without a matching property.", e.message)
    }

    @Test
    fun testRequiredPropertiesUndefined31() {
        assertEquals(
            listOf(
                "required property 'code' in Closed is not defined in its properties",
                "required property 'code' in ClosedUnevaluated is not defined in its properties",
                "required property 'nmae' in RefSibling is not defined in its properties",
            ),
            undefinedRequiredPropertyErrors("required-properties-undefined-31"),
        )
    }

    @Test
    fun testRequiredPropertiesDefined() {
        assertEquals(emptyList(), undefinedRequiredPropertyErrors("required-properties-defined"))
        validate("required-properties-defined")
    }

    @Test
    fun testRequiredPropertiesDefined31() {
        assertEquals(emptyList(), undefinedRequiredPropertyErrors("required-properties-defined-31"))
        validate("required-properties-defined-31")
    }
}
