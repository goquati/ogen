package de.quati.ogen.plugin.intern.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName
import de.quati.kotlin.util.poet.dsl.buildAnnotationSpec

internal object Poet {
    val serializer = ClassName("kotlinx.serialization.builtins", "serializer")
    val nullable = ClassName("kotlinx.serialization.builtins", "nullable")
    val kSerializer = ClassName("kotlinx.serialization", "KSerializer")
    val option = ClassName("de.quati.kotlin.util", "Option")
    val mapSerializer = ClassName("kotlinx.serialization.builtins", "MapSerializer")
    val listSerializer = ClassName("kotlinx.serialization.builtins", "ListSerializer")
    val serialDescriptor = ClassName("kotlinx.serialization.descriptors", "SerialDescriptor")
    val serializationException = ClassName("kotlinx.serialization", "SerializationException")
    val experimentalSerializationApi = ClassName("kotlinx.serialization", "ExperimentalSerializationApi")

    fun serializable(with: TypeName? = null) = buildAnnotationSpec(
        packageName = "kotlinx.serialization",
        className = "Serializable",
    ) {
        if (with != null)
            addMember("with = %T::class", with)
    }

    fun serialName(value: String) = buildAnnotationSpec(
        packageName = "kotlinx.serialization",
        className = "SerialName",
    ) {
        addMember("value = %S", value)
    }

    fun jsonClassDiscriminator(value: String) = buildAnnotationSpec(
        packageName = "kotlinx.serialization.json",
        className = "JsonClassDiscriminator",
    ) {
        addMember("%S", value)
    }
}