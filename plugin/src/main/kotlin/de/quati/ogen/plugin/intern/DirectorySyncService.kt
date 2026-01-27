package de.quati.ogen.plugin.intern

import com.squareup.kotlinpoet.FileSpec
import de.quati.kotlin.util.poet.PackageName
import org.gradle.api.file.Directory
import org.gradle.internal.logging.text.StyledTextOutput
import java.nio.file.Path
import kotlin.collections.forEach
import kotlin.collections.minus
import kotlin.collections.plus
import kotlin.collections.plusAssign
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.relativeToOrNull
import kotlin.io.path.walk
import kotlin.io.path.writeText
import kotlin.sequences.map
import kotlin.sequences.toSet
import kotlin.text.padStart

internal class DirectorySyncService(
    rootDir: Directory,
    private val packageName: PackageName,
    private val out: StyledTextOutput,
) {
    private val outDir = rootDir.dir(packageName.parts.joinToString("/")).asFile.toPath()
    private var filesCreated = mutableSetOf<Path>()
    private var filesUpdated = mutableSetOf<Path>()
    private var filesUnchanged = mutableSetOf<Path>()
    private var filesDeleted = mutableSetOf<Path>()

    fun sync(relativePath: String, content: String) = sync(
        path = outDir.resolve(relativePath).absolute(),
        content = content,
    )

    fun sync(
        relativePath: String,
        content: FileSpec,
    ) = sync(relativePath = relativePath, content = content.toString())

    fun sync(
        fileName: String,
        block: FileSpec.Builder.() -> Unit,
    ) {
        val fileSpec = FileSpec.builder(
            packageName = packageName.name,
            fileName = fileName,
        ).apply(block).build()
        sync(
            relativePath = fileName,
            content = fileSpec,
        )
    }

    private fun cleanup() {
        @OptIn(ExperimentalPathApi::class)
        val actualFiles = outDir.walk().map { it.absolute() }.toSet()
        val filesToDelete = actualFiles - (filesCreated + filesUpdated + filesUnchanged)
        filesToDelete.forEach { it.deleteExisting() }
        filesDeleted += filesToDelete

        fun Set<*>.printSize() = size.toString().padStart(3)
        out.withStyle(StyledTextOutput.Style.Info)
        out.println("package '$packageName' synced:")
        out.println("   #files unchanged = ${filesUnchanged.printSize()}")
        out.println("   #files created   = ${filesCreated.printSize()}")
        out.println("   #files updated   = ${filesUpdated.printSize()}")
        out.println("   #files deleted   = ${filesDeleted.printSize()}")
        out.println()
    }

    private fun checkFilePath(path: Path) {
        val inDirectory = null != path.absolute().relativeToOrNull(outDir.absolute())
        if (!inDirectory) error("path '$path' is not in output directory '$outDir'")
    }

    private fun sync(path: Path, content: String) {
        checkFilePath(path)
        val type = DirectorySyncService.sync(path = path, content = content)
        when (type) {
            FileSyncType.UNCHANGED -> filesUnchanged.add(path)
            FileSyncType.UPDATED -> filesUpdated.add(path)
            FileSyncType.CREATED -> filesCreated.add(path)
        }
    }

    enum class FileSyncType {
        UNCHANGED, UPDATED, CREATED
    }

    fun <T> use(block: (DirectorySyncService) -> T): T = try {
        val result = block(this)
        cleanup()
        result
    } finally {
    }

    companion object {
        private fun sync(
            path: Path,
            content: String,
        ): FileSyncType {
            if (!path.isAbsolute) return sync(path = path.absolute(), content = content)
            return if (path.exists()) {
                if (path.readText() == content) {
                    FileSyncType.UNCHANGED
                } else {
                    path.writeText(content)
                    FileSyncType.UPDATED
                }
            } else {
                path.parent.createDirectories()
                path.writeText(content)
                FileSyncType.CREATED
            }
        }
    }
}