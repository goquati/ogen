import de.quati.ogen.plugin.NameConvention
import de.quati.ogen.plugin.Severity
import de.quati.ogen.plugin.SpecsConfigBuilder
import de.quati.ogen.plugin.intern.tasks.Generator
import de.quati.ogen.plugin.intern.validation.namingConventionErrors
import de.quati.ogen.plugin.intern.validation.operationIdRequiredErrors
import org.slf4j.helpers.NOPLogger
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestOperationIdCheck {
    private val missing = listOf(
        "operation '/items:POST' has no operationId",
        "operation '/items/{itemId}:DELETE' has no operationId",
    )

    // The callback's operation has no operationId either, but ogen generates no method for it.
    @Test
    fun testMissingOperationIds() {
        assertErrors(
            "operation-id-missing", ::operationIdRequiredErrors, missing,
            validator = { rules { operationIdRequired = Severity.Error } },
        )
    }

    // The rule is off by default, so ogen names such operations itself without a finding.
    @Test
    fun testOffByDefault() {
        validate("operation-id-missing")
    }

    @Test
    fun testEveryOperationWithOperationId() {
        assertPasses("declared-tags", ::operationIdRequiredErrors)
    }

    // An empty operationId counts as none: the naming conventions don't check it.
    @Test
    fun testEmptyOperationIdIsNoName() {
        val config = validatorConfigs("operation-id-missing") { naming { operationId = NameConvention.CamelCase } }
            .specs.single().validatorConfig!!
        assertEquals(emptyList(), namingConventionErrors(parsedSpec("operation-id-missing"), config))
    }

    // An empty operationId, and an empty name from operationIdGenerator, count as none: the name is derived.
    @Test
    fun testEmptyOperationIdIsDerived() {
        listOf<String?>(null, "").forEach { generated ->
            val configs = SpecsConfigBuilder().apply {
                utilPackageName("de.quati.ogen.test.gen.util")
                add(packageName = "de.quati.ogen.test.gen") {
                    specFile(TestOperationIdCheck::class.java.getResource("/operation-id-missing.yaml")!!.path)
                    model {}
                    clientKtor {}
                    operationIdGenerator { generated }
                }
            }.build()
            val code = TestGeneration.withTempDirectory(prefix = "test-operation-id") { dir ->
                Generator(rootOutputDir = dir, logger = NOPLogger.NOP_LOGGER).generate(configs = configs)
                dir.walk().filter { it.toString().endsWith(".kt") }.joinToString("\n") { it.readText() }
            }
            assertTrue("fun itemsItemIdDelete(" in code, code)
        }
    }
}
