package de.quati.ogen.plugin.intern.model.config

import org.openapitools.codegen.config.MergedSpecBuilder
import java.nio.file.Path

internal sealed interface InputConfig {
    data class File(val path: Path) : InputConfig {
        override fun toString(): String = path.toString()
    }
    data class Directory(
        val path: Path,
        val mergeFileName: String,
        val mergedFileInfoName: String,
        val mergedFileInfoDescription: String,
        val mergedFileInfoVersion: String,
        val mergedFileAuth: String?,
    ) : InputConfig {
        override fun toString(): String = path.toString()

        fun toMergedSpecBuilder() = MergedSpecBuilder(
            path.toString(),
            mergeFileName,
            mergedFileInfoName,
            mergedFileInfoDescription,
            mergedFileInfoVersion,
            mergedFileAuth
        )
    }
}
