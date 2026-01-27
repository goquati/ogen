package de.quati.ogen.plugin.intern.codegen.constant

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeVariableName
import de.quati.kotlin.util.poet.dsl.addFunction
import de.quati.kotlin.util.poet.dsl.addProperty
import de.quati.kotlin.util.poet.dsl.buildClass
import de.quati.kotlin.util.poet.dsl.primaryConstructor
import de.quati.ogen.plugin.intern.codegen.Poet

internal val valueSerializerTypeSpec
    get() = buildClass("ValueSerializer") {
        val t = TypeVariableName("T")
        val v = TypeVariableName("V")
        addTypeVariables(listOf(v, t))
        addSuperinterface(Poet.kSerializer.parameterizedBy(v))
        primaryConstructor {
            run {
                val name = "inner"
                val type = Poet.kSerializer.parameterizedBy(t)
                addParameter(name, type)
                addProperty(name, type) {
                    addModifiers(KModifier.PRIVATE)
                    initializer(name)
                }
            }
            run {
                val name = "unwrap"
                val type = LambdaTypeName.get(
                    parameters = listOf(ParameterSpec.unnamed(v)),
                    returnType = t,
                )
                addParameter(name, type)
                addProperty(name, type) {
                    addModifiers(KModifier.PRIVATE)
                    initializer(name)
                }
            }
            run {
                val name = "wrap"
                val type = LambdaTypeName.get(
                    parameters = listOf(ParameterSpec.unnamed(t)),
                    returnType = v,
                )
                addParameter(name, type)
                addProperty(name, type) {
                    addModifiers(KModifier.PRIVATE)
                    initializer(name)
                }
            }
        }

        addProperty("descriptor", Poet.serialDescriptor) {
            addModifiers(KModifier.OVERRIDE)
            initializer("inner.descriptor")
        }
        addFunction("serialize") {
            addModifiers(KModifier.OVERRIDE)
            addParameter(
                "encoder",
                ClassName("kotlinx.serialization.encoding", "Encoder"),
            )
            addParameter("value", v)
            addStatement("encoder.encodeSerializableValue(inner, unwrap(value))")
        }
        addFunction("deserialize") {
            addModifiers(KModifier.OVERRIDE)
            returns(v)
            addParameter(
                "decoder",
                ClassName("kotlinx.serialization.encoding", "Decoder"),
            )
            addStatement("val innerValue =decoder.decodeSerializableValue(inner)")
            addStatement("return wrap(innerValue)")
        }
    }
