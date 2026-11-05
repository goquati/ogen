import de.quati.ogen.plugin.SpecsConfigBuilder
import de.quati.ogen.plugin.intern.model.config.SpecConfig
import de.quati.ogen.plugin.intern.tasks.Validator
import de.quati.ogen.plugin.intern.tasks.nullableWithoutTypeWarnings
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.SpecVersion
import io.swagger.v3.oas.models.media.Schema
import org.gradle.api.GradleException
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TestValidator {
    companion object {
        private val logger = LoggerFactory.getLogger(TestValidator::class.java)!!
        private const val NULLABLE_HINT =
            "in OpenAPI 3.0 it only takes effect next to a 'type'; a nullable \$ref goes in an 'allOf'"

        private fun specConfig(
            spec: String,
            validator: SpecsConfigBuilder.SpecConfingBuilder.ValidatorConfig.() -> Unit = {},
        ): SpecConfig = SpecsConfigBuilder().apply {
            utilPackageName("de.quati.ogen.test.gen.util")
            add(packageName = "de.quati.ogen.test.gen") {
                specFile(TestValidator::class.java.getResource("$spec.yaml")!!.path)
                model {}
                validator(validator)
            }
        }.build().specs.single()

        private fun nullableWithoutTypeMessages(spec: String) =
            nullableWithoutTypeMessages(specConfig(spec).parseResult.openAPI)

        private fun nullableWithoutTypeMessages(spec: OpenAPI) =
            nullableWithoutTypeWarnings(spec).warnings.map { it.message }

        // Built by hand: the parser drops `nullable` in 3.1 and next to a `$ref` in 3.0, so no YAML reaches these cases.
        private fun specWithSchema(version: SpecVersion, schema: Schema<*>) =
            OpenAPI().specVersion(version).components(Components().addSchemas("Pet", schema))
    }

    @Test
    fun testNullableWithoutType() {
        assertEquals(
            listOf(
                "Pet.name",
                "Pet.labels.additionalProperties",
                "Pet.other.not",
                "Dog.allOf[1].breed",
                "components.parameters.Limit",
                "components.requestBodies.NewPet application/json",
                "components.responses.Pets application/json",
                "components.headers.Location",
                "/pets:GET.X-Trace",
                "/pets:POST.filter application/json",
                "/pets:POST request body application/json.name",
                "/pets:POST response 200 application/json[]",
                "/pets:POST response 200 header X-Rate",
            ).map { "'nullable: true' without a 'type' in $it: $NULLABLE_HINT" },
            nullableWithoutTypeMessages("nullable-without-type"),
        )
    }

    @Test
    fun testNullableWithoutTypeFailsOnWarnings() {
        val config = specConfig("nullable-without-type-property") { failOnWarnings = true }
        val e = assertFailsWith<GradleException> { Validator.validate(config = config, logger = logger) }
        assertEquals("Validation failed due to warnings (failOnWarnings = true).", e.message)
    }

    @Test
    fun testNullableWithTypeOrComposition() {
        assertEquals(emptyList(), nullableWithoutTypeMessages("nullable-without-type-valid"))
    }

    @Test
    fun testNullableWithoutTypeWithRef() {
        val schema = Schema<Any>().nullable(true).also { it.`$ref` = "#/components/schemas/Owner" }
        assertEquals(emptyList(), nullableWithoutTypeMessages(specWithSchema(SpecVersion.V30, schema)))
    }

    @Test
    fun testNullableWithoutTypeIgnoredInOpenApi31() {
        val schema = Schema<Any>().nullable(true)
        assertEquals(1, nullableWithoutTypeMessages(specWithSchema(SpecVersion.V30, schema)).size)
        assertEquals(emptyList(), nullableWithoutTypeMessages(specWithSchema(SpecVersion.V31, schema)))
    }

    @Test
    fun testInvalidYamlFailsValidation() {
        val e = assertFailsWith<GradleException> { Validator.validate(config = specConfig("invalid-yaml"), logger = logger) }
        assertEquals("Validation failed.", e.message)
    }
}
