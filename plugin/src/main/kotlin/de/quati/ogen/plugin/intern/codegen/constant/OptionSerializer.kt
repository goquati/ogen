package de.quati.ogen.plugin.intern.codegen.constant

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeVariableName
import de.quati.kotlin.util.poet.dsl.addCode
import de.quati.kotlin.util.poet.dsl.addControlFlow
import de.quati.kotlin.util.poet.dsl.addFunction
import de.quati.kotlin.util.poet.dsl.addProperty
import de.quati.kotlin.util.poet.dsl.buildClass
import de.quati.kotlin.util.poet.dsl.primaryConstructor
import de.quati.ogen.plugin.intern.codegen.Poet

internal val optionSerializerTypeSpec
    get() = buildClass("OptionSerializer") {
        val t = TypeVariableName("T")
        addTypeVariable(t)
        addSuperinterface(Poet.kSerializer.parameterizedBy(Poet.option.parameterizedBy(t)))
        primaryConstructor {
            addParameter("valueSerializer", Poet.kSerializer.parameterizedBy(t))
        }
        addProperty("valueSerializer", Poet.kSerializer.parameterizedBy(t)) {
            addModifiers(KModifier.PRIVATE)
            initializer("valueSerializer")
        }
        addProperty("descriptor", Poet.serialDescriptor) {
            addModifiers(KModifier.OVERRIDE)
            initializer("valueSerializer.descriptor")
        }
        addFunction("serialize") {
            addModifiers(KModifier.OVERRIDE)
            addParameter(
                "encoder",
                ClassName("kotlinx.serialization.encoding", "Encoder"),
            )
            addParameter("value", Poet.option.parameterizedBy(t))
            addCode {
                addControlFlow("when (value)") {
                    addStatement(
                        "is %T -> encoder.encodeSerializableValue(valueSerializer, value.value)",
                        Poet.option.nestedClass("Some").parameterizedBy(t),
                    )
                    addStatement(
                        "%T -> throw %T(%S)",
                        Poet.option.nestedClass("Undefined"),
                        Poet.serializationException,
                        "Option.Undefined must be omitted (use a default value + Json { encodeDefaults = false })."
                    )
                }
            }
        }
        addFunction("deserialize") {
            addModifiers(KModifier.OVERRIDE)
            returns(Poet.option.parameterizedBy(t))
            addParameter(
                "decoder",
                ClassName("kotlinx.serialization.encoding", "Decoder"),
            )
            addCode {
                addStatement("// If the field is absent, this serializer is not called at all -> default Option.Undefined is used.")
                addStatement("// If the field is present, we decode it as T (respecting T's nullability).")
                addStatement(
                    "return %T(decoder.decodeSerializableValue(valueSerializer))",
                    Poet.option.nestedClass("Some"),
                )
            }
        }
    }
