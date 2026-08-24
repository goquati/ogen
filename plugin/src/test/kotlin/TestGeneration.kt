import de.quati.ogen.plugin.SpecsConfigBuilder
import de.quati.ogen.plugin.intern.tasks.Generator
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test

class TestGeneration {
    companion object {
        private val logger = LoggerFactory.getLogger(TestGeneration::class.java)!!

        inline fun <T> withTempDirectory(prefix: String = "tmp", block: (Path) -> T): T {
            val dir = createTempDirectory(prefix)
            try {
                return block(dir)
            } finally {
                dir.toFile().deleteRecursively()
            }
        }

        private fun testClientGenSpec(spec: String) {
            val group = "de.quati.ogen.test"
            val configs = SpecsConfigBuilder().apply {
                utilPackageName("$group.gen.util")
                add(packageName = "$group.gen") {
                    specFile(TestGeneration::class.java.getResource("$spec.yaml")!!.path)
                    model {}
                    clientKtor {}
                }
            }.build()

            withTempDirectory(prefix = "test-oas-client-gen-spec") {
                Generator(
                    rootOutputDir = it,
                    logger = logger,
                ).generate(configs = configs)
            }
        }
    }

    @Test
    fun testClientGenSpecCompute() {
        testClientGenSpec("compute")
    }

    @Test
    fun testClientGenSpecIdentity() {
        testClientGenSpec("identity")
    }

    @Test
    fun testClientGenSpecImage() {
        testClientGenSpec("image")
    }

    @Test
    fun testClientGenSpecNetwork() {
        testClientGenSpec("network")
    }

    @Test
    fun testClientGenSpecStorage() {
        testClientGenSpec("storage")
    }
}