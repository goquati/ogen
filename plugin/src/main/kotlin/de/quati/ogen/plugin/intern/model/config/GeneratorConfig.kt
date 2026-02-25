package de.quati.ogen.plugin.intern.model.config

import com.squareup.kotlinpoet.ClassName
import de.quati.kotlin.util.poet.PackageName
import de.quati.ogen.plugin.intern.model.ComponentName
import de.quati.ogen.plugin.intern.model.Type
import de.quati.ogen.plugin.intern.model.TypeWithFormat


internal sealed interface GeneratorConfig {
    val packageName: PackageName
    val skipGeneration: Boolean

    data class Model(
        override val packageName: PackageName,
        override val skipGeneration: Boolean,
        val typeMappings: Map<TypeWithFormat, Type.NonPrimitiveType.Custom>,
        val schemaMappings: Map<ComponentName.Schema, Type.NonPrimitiveType.Custom>,
        val postfix: String,
    ) : GeneratorConfig

    data class ServerSpringV4(
        override val packageName: PackageName,
        override val skipGeneration: Boolean,
        val postfix: String,
        val contextIfAnySecurity: ClassName?,
        val addOperationContext: Boolean,
    ) : GeneratorConfig

    data class ClientKtor(
        override val packageName: PackageName,
        override val skipGeneration: Boolean,
        val postfix: String,
    ) : GeneratorConfig
}