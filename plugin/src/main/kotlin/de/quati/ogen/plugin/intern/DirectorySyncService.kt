package de.quati.ogen.plugin.intern

import com.squareup.kotlinpoet.FileSpec
import de.quati.kotlin.util.poet.PackageName
import org.slf4j.Logger
import java.nio.file.Path
import kotlin.collections.forEach
import kotlin.collections.minus
import kotlin.collections.plus
import kotlin.collections.plusAssign
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.relativeToOrNull
import kotlin.io.path.writeText
import kotlin.text.padStart

private fun Logger.lifecycle(message: String) {
    if (this is org.gradle.api.logging.Logger)
        this.lifecycle(message)
    else
        info(message)
}

internal class DirectorySyncService(
    rootDir: Path,
    private val packageName: PackageName,
    private val logger: Logger,
) {
    private val outDir = rootDir.resolve(packageName.parts.joinToString("/"))
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
        val actualFiles = outDir.listDirectoryEntries().map { it.absolute() }
            .filter { it.isRegularFile() }.toSet()
        val filesToDelete = actualFiles - (filesCreated + filesUpdated + filesUnchanged)
        filesToDelete.forEach { it.deleteExisting() }
        filesDeleted += filesToDelete

        fun Set<*>.printSize() = size.toString().padStart(3)
        logger.lifecycle("package '$packageName' synced:")
        logger.lifecycle("   #files unchanged = ${filesUnchanged.printSize()}")
        logger.lifecycle("   #files created   = ${filesCreated.printSize()}")
        logger.lifecycle("   #files updated   = ${filesUpdated.printSize()}")
        logger.lifecycle("   #files deleted   = ${filesDeleted.printSize()}")
        logger.lifecycle("")
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