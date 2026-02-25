package de.quati.ogen.plugin.intern.model.config

import java.nio.file.Path

internal sealed interface InputConfig {
    enum class Format {
        JSON, YAML;

        companion object {
            fun parse(fileName: String) = fileName.lowercase().let {
                if (it.endsWith(".json")) JSON
                else YAML
            }
        }
    }

    data class File(val path: Path) : InputConfig {
        override fun toString(): String = path.toString()
    }

    data class Merge(
        val directoryPath: Path,
        val mergeFileName: String,
        val baseConfig: BaseConfig,
    ) : InputConfig {
        sealed interface BaseConfig {
            data class File(val path: Path) : BaseConfig {
                val format get() = Format.parse(path.toString())
            }

            data class Data(
                val infoTitle: String,
                val infoDescription: String?,
                val infoVersion: String,
                val additional: Map<String, Any?>,
            ) : BaseConfig
        }

        internal fun mergeFilePath(format: Format): Path =
            directoryPath.resolve(
                mergeFileName + when (format) {
                    Format.JSON -> ".json"
                    Format.YAML -> ".yaml"
                }
            )!!

        override fun toString(): String = directoryPath.toString()
    }
}
