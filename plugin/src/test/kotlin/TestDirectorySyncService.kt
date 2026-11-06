import de.quati.kotlin.util.poet.PackageName
import de.quati.ogen.plugin.intern.DirectorySyncService
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestDirectorySyncService {
    private val logger = LoggerFactory.getLogger(TestDirectorySyncService::class.java)!!
    private val packageName = PackageName("de.quati.ogen.test.gen.model")

    @TempDir
    lateinit var root: Path

    private fun sync(block: (DirectorySyncService) -> Unit) =
        DirectorySyncService(rootDir = root, packageName = packageName, logger = logger).use(block)

    @Test
    fun testPackageWithoutFiles() {
        sync { }
        assertFalse(root.resolve("de/quati/ogen/test/gen/model").exists())
    }

    // A package that had files in an earlier run and gets none now is emptied.
    @Test
    fun testStaleFilesOfAPackageWithoutFiles() {
        val file = root.resolve("de/quati/ogen/test/gen/model/ItemDto.kt")
        sync { it.sync(relativePath = "ItemDto.kt", content = "class ItemDto") }
        assertTrue(file.exists())
        sync { }
        assertFalse(file.exists())
    }
}
