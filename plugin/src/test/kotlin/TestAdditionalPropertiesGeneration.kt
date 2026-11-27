import de.quati.ogen.plugin.SpecsConfigBuilder
import de.quati.ogen.plugin.intern.tasks.Generator
import org.slf4j.LoggerFactory
import kotlin.io.path.createTempDirectory
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

/** Generated code of classes with additional properties that the test projects' default configuration can't reach (#108). */
class TestAdditionalPropertiesGeneration {
    private fun generate(): Map<String, String> {
        val configs = SpecsConfigBuilder().apply {
            utilPackageName("de.quati.ogen.test.gen.util")
            add(packageName = "de.quati.ogen.test.gen") {
                specFile(TestAdditionalPropertiesGeneration::class.java.getResource("additional-properties-generation.yaml")!!.path)
                model { postfix = "" }
                clientKtor {}
            }
        }.build()
        val dir = createTempDirectory("test-additional-properties")
        try {
            Generator(rootOutputDir = dir, logger = LoggerFactory.getLogger(TestAdditionalPropertiesGeneration::class.java)).generate(configs = configs)
            return dir.walk().associate { it.name to it.readText() }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `the serializer object doesn't clash with a nested class`() {
        val clash = generate().getValue("Clash.kt")
        assertContains(clash, "public data class Serializer(")
        assertContains(clash, "@Serializable(with = Clash.Serializer_::class)")
        assertContains(clash, "public object Serializer_ : KSerializer<Clash>")
    }

    @Test
    fun `a multipart body with additional properties is passed whole`() {
        val client = generate().entries.single { "suspend fun upload(" in it.value }.value
        val upload = client.substringAfter("suspend fun upload(").substringBefore(")")
        assertFalse("file:" in upload, upload)
        assertContains(upload, "uploadRequestBody: Any")
    }
}
