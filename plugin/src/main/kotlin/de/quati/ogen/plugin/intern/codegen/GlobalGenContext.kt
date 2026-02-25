package de.quati.ogen.plugin.intern.codegen

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import de.quati.kotlin.util.poet.dsl.buildObject
import de.quati.ogen.plugin.intern.model.Type
import de.quati.ogen.plugin.intern.model.config.SpecConfigs

internal class GlobalGenContext(
    val specConfigs: SpecConfigs,
) {
    val utilConfig get() = specConfigs.util
    val serverSpringV4EnumConversionTypes = mutableListOf<Type>()

    private val serializerTypeSpecBuilder = TypeSpec.objectBuilder("Serializer")
        .addModifiers(KModifier.INTERNAL)

    fun buildSerializerTypeSpec() = serializerTypeSpecBuilder.build()
    private val serializers = mutableMapOf<Type, String>()

    fun registerSerializer(
        register: Boolean,
        type: Type,
        delegate: CodeBlock,
    ): TypeName {
        fun serializerClassName(name: String) = utilConfig.model.packageName.className("Serializer", name)
        serializers[type]?.let {
            return serializerClassName(it)
        }

        val name = type.prettyName()
        val typeName = serializerClassName(name)
        if (!register) return typeName
        serializerTypeSpecBuilder.addType(buildObject(name) {
            addSuperinterface(
                Poet.kSerializer.parameterizedBy(type.poet),
                delegate = delegate
            )
        })
        serializers[type] = name
        return typeName
    }
}