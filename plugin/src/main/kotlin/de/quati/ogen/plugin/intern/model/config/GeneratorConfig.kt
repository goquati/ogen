package de.quati.ogen.plugin.intern.model.config

import com.squareup.kotlinpoet.ClassName
import de.quati.kotlin.util.poet.PackageName


internal sealed interface GeneratorConfig {
    val packageName: PackageName

    data class ServerSpringV4(
        override val packageName: PackageName,
        val postfix: String,
        val contextIfAnySecurity: ClassName?,
        val addOperationContext: Boolean,
    ) : GeneratorConfig
}