import de.quati.ogen.plugin.SpecsConfigBuilder
import de.quati.ogen.plugin.intern.tasks.Generator
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class TestBeanValidation {
    private val logger = LoggerFactory.getLogger(TestBeanValidation::class.java)!!

    private fun generate(spec: String, beanValidation: Boolean): Map<String, String> {
        val group = "de.quati.ogen.test"
        val configs = SpecsConfigBuilder().apply {
            utilPackageName("$group.gen.util")
            add(packageName = "$group.gen") {
                specFile(TestBeanValidation::class.java.getResource("$spec.yaml")!!.path)
                model {
                    typeMapping(type = "number+big-decimal", clazz = "java.math.BigDecimal")
                    typeMapping(type = "string+date-time", clazz = "java.time.OffsetDateTime")
                }
                serverSpringV4 { this.beanValidation = beanValidation }
            }
        }.build()
        return TestGeneration.withTempDirectory(prefix = "test-bean-validation") { dir ->
            Generator(rootOutputDir = dir, logger = logger).generate(configs = configs)
            dir.walk().filter { it.toString().endsWith(".kt") }
                .associate { it.fileName.toString() to it.readText() }
        }
    }

    private fun Map<String, String>.file(name: String) = getValue(name).replace(Regex("\\s+"), " ")

    @Test
    fun `parameters with OpenAPI 3_1 bounds`() {
        val api = generate("bean-validation-3.1", beanValidation = true).file("ThingsApi.kt")
        // exclusive integer bounds become inclusive ones
        assertContains(api, "@PathVariable(value = \"id\", required = true) @Min(value = 1L) id: Long")
        assertContains(api, "@DecimalMin(value = \"0.5\") @DecimalMax(value = \"2\", inclusive = false) ratio: Double?")
        // fractional bounds of integers are rounded inwards
        assertContains(api, "@Min(value = 2L) @Max(value = 7L) fraction: Int?")
    }

    @Test
    fun `model properties`() {
        val model = generate("bean-validation-3.1", beanValidation = true).file("ThingDto.kt")
        // of `minimum` and `exclusiveMinimum` at the same value, the exclusive one is tighter
        assertContains(model, "@field:DecimalMin( value = \"10\", inclusive = false, ) public val amount: BigDecimal")
        assertContains(model, "@field:Size(max = 4)")
        // string keywords don't apply to a type mapped to a class other than a string
        assertFalse(model.contains("@field:Size(max = 40)"))
    }

    @Test
    fun `no annotations without beanValidation`() {
        val files = generate("bean-validation-3.1", beanValidation = false)
        files.values.forEach { assertFalse(it.contains("jakarta.validation")) }
    }

    @Test
    fun `large specs generate`() {
        listOf("compute", "identity", "image", "network", "storage", "keycloak-26.0.8").forEach {
            generate(it, beanValidation = true)
        }
    }
}
