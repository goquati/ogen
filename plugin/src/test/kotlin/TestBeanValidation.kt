import de.quati.ogen.plugin.SpecsConfigBuilder
import de.quati.ogen.plugin.intern.tasks.Generator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.reflect.Proxy
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TestBeanValidation {
    private val delegate = LoggerFactory.getLogger(TestBeanValidation::class.java)!!
    private val warnings = mutableListOf<String>()

    /** Records the warnings, logs everything. */
    private val logger = Proxy.newProxyInstance(
        Logger::class.java.classLoader,
        arrayOf(Logger::class.java),
    ) { _, method, args ->
        if (method.name == "warn" && args?.firstOrNull() is String) warnings += args.first() as String
        method.invoke(delegate, *(args ?: emptyArray()))
    } as Logger

    private fun generate(
        spec: String,
        modelBeanValidation: Boolean = true,
        serverBeanValidation: Boolean = true,
        multiplatform: Boolean = false,
    ): Map<String, String> {
        val group = "de.quati.ogen.test"
        val configs = SpecsConfigBuilder().apply {
            utilPackageName("$group.gen.util")
            add(packageName = "$group.gen") {
                specFile(TestBeanValidation::class.java.getResource("$spec.yaml")!!.path)
                // Tests of generation only; some specs, such as keycloak's, have errors the validator reports.
                validator { enabled = false }
                model {
                    typeMapping(type = "number+big-decimal", clazz = "java.math.BigDecimal")
                    typeMapping(type = "string+date-time", clazz = "java.time.OffsetDateTime")
                    typeMapping(type = "integer+unix-time", clazz = "kotlin.Long")
                    typeMapping(type = "string+secret", clazz = "kotlin.String")
                    beanValidation = modelBeanValidation
                }
                serverSpringV4 { beanValidation = serverBeanValidation }
            }
        }.build()
        return TestGeneration.withTempDirectory(prefix = "test-bean-validation") { dir ->
            Generator(rootOutputDir = dir, logger = logger, multiplatform = multiplatform).generate(configs = configs)
            dir.walk().filter { it.toString().endsWith(".kt") }
                .associate { it.fileName.toString() to it.readText() }
        }
    }

    private fun Map<String, String>.file(name: String) = getValue(name).replace(Regex("\\s+"), " ")

    @Test
    fun `parameters with OpenAPI 3_1 bounds`() {
        val api = generate("bean-validation-3.1").file("ThingsApi.kt")
        // exclusive integer bounds become inclusive ones
        assertContains(api, "@PathVariable(value = \"id\", required = true) @Min(value = 1L) id: Long")
        assertContains(api, "@DecimalMin(value = \"0.5\") @DecimalMax(value = \"2\", inclusive = false) ratio: Double?")
        // fractional bounds of integers are rounded inwards
        assertContains(api, "@Min(value = 2L) @Max(value = 7L) fraction: Int?")
    }

    @Test
    fun `model properties`() {
        val model = generate("bean-validation-3.1").file("ThingDto.kt")
        // of `minimum` and `exclusiveMinimum` at the same value, the exclusive one is tighter
        assertContains(model, "@field:DecimalMin( value = \"10\", inclusive = false, ) public val amount: BigDecimal")
        assertContains(model, "@field:Size(max = 4)")
        // string keywords don't apply to a type mapped to a class other than a string
        assertFalse(model.contains("@field:Size(max = 40)"))
    }

    @Test
    fun `no annotations without beanValidation`() {
        val files = generate("bean-validation-3.1", modelBeanValidation = false, serverBeanValidation = false)
        files.values.forEach { assertFalse(it.contains("validation")) }
    }

    @Test
    fun `model and server flags are independent`() {
        generate("bean-validation-3.1", serverBeanValidation = false).also {
            assertContains(it.file("ThingDto.kt"), "@field:Size(max = 4)")
            assertFalse(it.file("ThingsApi.kt").contains("jakarta.validation"))
        }
        generate("bean-validation-3.1", modelBeanValidation = false).also {
            assertFalse(it.file("ThingDto.kt").contains("jakarta.validation"))
            assertContains(it.file("ThingsApi.kt"), "@Min(value = 1L) id: Long")
        }
    }

    @Test
    fun `Jakarta annotations for the JVM, those of core for multiplatform models`() {
        generate("bean-validation-3.1").also {
            assertContains(it.getValue("ThingDto.kt"), "import jakarta.validation.constraints.Size")
            assertContains(it.getValue("ThingsApi.kt"), "import jakarta.validation.constraints.Min")
            // only MultipleOf and UniqueItems, which Jakarta has none for, come from core
            val standIns = Regex("import de\\.quati\\.ogen\\.core\\.validation\\.(?!MultipleOf|UniqueItems)")
            it.values.forEach { file -> assertFalse(standIns.containsMatchIn(file)) }
        }
        generate("bean-validation-3.1", multiplatform = true).also {
            val model = it.getValue("ThingDto.kt")
            assertContains(model, "import de.quati.ogen.core.validation.Size")
            assertFalse(model.contains("jakarta.validation"))
            // the server is JVM code in any case
            assertContains(it.getValue("ThingsApi.kt"), "import jakarta.validation.constraints.Min")
        }
    }

    @Test
    fun `constraints of items and values on the type arguments`() {
        val model = generate("bean-validation-3.1").file("ThingDto.kt")
        assertContains(model, "Option<List<@Size(max = 2) List<@Pattern(regexp = \"^[a-z]+${'$'}{'${'$'}'}\") String>>>")
        assertContains(model, "Option<Map<String, @Max(value = 10L) Int>>")
        // Spring only validates a parameter with a constraint or `@Valid` on itself
        val api = generate("bean-validation-3.1").file("ThingsApi.kt")
        assertContains(api, "@RequestParam(value = \"words\", required = false) @Valid words: List<@Size(max = 3) String>?")
        // in multiplatform code the type arguments carry the annotations of core, too
        val kmpModel = generate("bean-validation-3.1", multiplatform = true).getValue("ThingDto.kt")
        assertContains(kmpModel, "import de.quati.ogen.core.validation.Max")
        assertFalse(kmpModel.contains("jakarta"))
    }

    @Test
    fun `multipleOf and uniqueItems with the constraints of core`() {
        listOf(false, true).forEach { multiplatform ->
            val model = generate("bean-validation-3.1", multiplatform = multiplatform).file("ThingDto.kt")
            assertContains(model, "import de.quati.ogen.core.validation.MultipleOf")
            assertContains(model, "import de.quati.ogen.core.validation.UniqueItems")
            assertContains(model, "@field:MultipleOf(value = \"5\")")
            // also for a type mapped to BigDecimal, without trailing zeros
            assertContains(model, "@field:MultipleOf(value = \"0.01\") @Serializable(with = Serializer.OptionBigDecimal::class) public val price: Option<BigDecimal>")
            assertContains(model, "@field:UniqueItems")
        }
    }

    @Test
    fun `warning when the server validates bodies whose models have no annotations`() {
        generate("bean-validation-3.1", modelBeanValidation = false)
        assertEquals(1, warnings.count { "model { beanValidation = true }" in it })

        warnings.clear()
        generate("bean-validation-3.1")
        generate("bean-validation-3.1", serverBeanValidation = false)
        generate("bean-validation-3.1", modelBeanValidation = false, serverBeanValidation = false)
        assertEquals(emptyList(), warnings.filter { "beanValidation" in it })
    }

    @Test
    fun `responses differing only in validation keywords have one type`() {
        listOf(true, false).forEach { enabled ->
            val api = generate("bean-validation-edge-cases", modelBeanValidation = enabled, serverBeanValidation = enabled)
                .file("LeavesApi.kt")
            assertContains(api, "fun getLeaves(): ResponseEntity<Flow<LeafDto>>")
        }
    }

    @Test
    fun `self-referencing schemas generate`() {
        // Tree and Node contain themselves; this would overflow the stack if the cascade check didn't end
        val files = generate("bean-validation-edge-cases")
        assertContains(files.keys, "TreeDto.kt")
        assertContains(files.keys, "NodeDto.kt")
    }

    @Test
    fun `reference to a type-mapped schema keeps its bounds`() {
        val model = generate("bean-validation-edge-cases").file("EdgeDto.kt")
        assertContains(model, "@field:DecimalMin(value = \"0\") @Serializable(with = Serializer.OptionBigDecimal::class) public val amount: Option<BigDecimal>")
    }

    @Test
    fun `patterns are only kept unchanged when anchored as a whole`() {
        val model = generate("bean-validation-edge-cases").file("EdgeDto.kt")
        val any = "[\\\\s\\\\S]*"
        assertContains(model, "@field:Pattern(regexp = \"$any(?:^foo|bar${'$'}{'${'$'}'})$any\")")
        assertContains(model, "@field:Pattern(regexp = \"^[a-z]+${'$'}{'${'$'}'}\")")
        assertContains(model, "@field:Pattern(regexp = \"^(foo|bar)${'$'}{'${'$'}'}\")")
        assertContains(model, "@field:Pattern(regexp = \"$any(?:^foo\\\\${'$'}{'${'$'}'})$any\")")
    }

    @Test
    fun `nested character classes don't hide the anchors`() {
        val model = generate("bean-validation-edge-cases").file("EdgeDto.kt")
        assertContains(model, "@field:Pattern(regexp = \"^[a-z&&[^x]|]+${'$'}{'${'$'}'}\")")
    }

    @Test
    fun `types mapped to Kotlin integers and strings keep their constraints`() {
        val model = generate("bean-validation-edge-cases").file("EdgeDto.kt")
        assertContains(model, Regex("@field:Max\\(value = 100L\\) @Serializable\\([^)]*\\) public val mappedToLong: Option<Long>"))
        assertContains(model, Regex("@field:Size\\(max = 8\\) @Serializable\\([^)]*\\) public val mappedToString: Option<String>"))
    }

    @Test
    fun `an optional JSON body without a schema stays nullable`() {
        listOf(true, false).forEach { enabled ->
            val api = generate("bean-validation-edge-cases", modelBeanValidation = enabled, serverBeanValidation = enabled)
                .file("RawApi.kt")
            assertContains(api, "fun postRaw(@RequestBody body: Any?)")
        }
    }

    @Test
    fun `integer bounds at and beyond the range of Long`() {
        val model = generate("bean-validation-edge-cases").file("EdgeDto.kt")
        assertContains(model, "@field:Min(value = Long.MIN_VALUE)")
        assertContains(model, "@field:DecimalMin(value = \"10000000000000000000\")")
    }

    @Test
    fun `large specs generate`() {
        listOf("compute", "identity", "image", "network", "storage", "keycloak-26.0.8").forEach {
            generate(it)
        }
    }
}
