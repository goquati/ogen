package de.quati.ogen.plugin.intern.codegen.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import de.quati.kotlin.util.poet.dsl.addClass
import de.quati.kotlin.util.poet.dsl.addCode
import de.quati.kotlin.util.poet.dsl.addControlFlow
import de.quati.kotlin.util.poet.dsl.addFunction
import de.quati.kotlin.util.poet.dsl.addProperty
import de.quati.kotlin.util.poet.dsl.buildClass
import de.quati.kotlin.util.poet.dsl.buildInterface
import de.quati.kotlin.util.poet.dsl.primaryConstructor
import de.quati.ogen.plugin.intern.DirectorySyncService
import de.quati.ogen.plugin.intern.codegen.Poet
import de.quati.ogen.plugin.intern.codegen.addConstructorProperty
import de.quati.ogen.plugin.intern.model.config.GeneratorConfig
import io.swagger.v3.oas.models.security.SecurityScheme


context(d: DirectorySyncService)
internal fun GeneratorConfig.Shared.sync() {
    d.sync(fileName = "ValueSerializer.kt") {
        addType(valueSerializerTypeSpec)
    }
    d.sync(fileName = "OptionSerializer.kt") {
        addType(optionSerializerTypeSpec)
    }
    d.sync(fileName = "OperationContext.kt") {
        addType(operationContextTypeSpec)
    }
    d.sync(fileName = "SecurityRequirementObject.kt") {
        addType(securityRequirementObjectTypeSpec)
    }
    d.sync(fileName = "SecurityRequirement.kt") {
        addType(securityRequirementTypeSpec)
    }
}

context(config: GeneratorConfig.Shared)
private val securityRequirementObjectTypeSpec
    get() = buildInterface("SecurityRequirementObject") {
        addModifiers(KModifier.SEALED)
        addProperty(name = "name", type = String::class)
        SecurityScheme.Type.entries.forEach { type ->
            addClass(type.prettyName) {
                addModifiers(KModifier.DATA)
                addSuperinterface(config.securityRequirementObject)
                primaryConstructor {
                    addConstructorProperty(
                        "name",
                        String::class.asClassName(),
                    ) { _, prop -> prop.addModifiers(KModifier.OVERRIDE) }
                }
            }
        }
    }

context(config: GeneratorConfig.Shared)
private val securityRequirementTypeSpec
    get() = buildClass("SecurityRequirement") {
        addModifiers(KModifier.VALUE)
        addAnnotation(Poet.jvmInline)
        primaryConstructor {
            addConstructorProperty(
                name = "requirements",
                type = List::class.asClassName().parameterizedBy(config.securityRequirementObject),
            )
        }
    }

context(config: GeneratorConfig.Shared)
private val operationContextTypeSpec
    get() = buildInterface("OperationContext") {
        addProperty(name = "name", type = String::class)
        addProperty(name = "description", type = String::class.asClassName().copy(nullable = true))
        addProperty(name = "deprecated", type = Boolean::class)
        addProperty(name = "tag", type = String::class.asClassName())
        addProperty(
            name = "security",
            type = List::class.asClassName().parameterizedBy(config.securityRequirement),
        )
        val bodyTypeName = config.packageName.className("OperationContext", "Body")
        addProperty(name = "requestBody", type = bodyTypeName.copy(nullable = true))
        addProperty(
            name = "responses",
            type = Map::class.asClassName().parameterizedBy(String::class.asClassName(), bodyTypeName)
        )
        addProperty(
            name = "defaultSuccessStatus",
            type = Int::class,
        )

        addClass(name = "Body") {
            addModifiers(KModifier.DATA)
            primaryConstructor {
                addParameter("description", String::class.asClassName().copy(nullable = true))
                addParameter("contentTypes", Set::class.asClassName().parameterizedBy(String::class.asClassName()))
            }
            addProperty(name = "description", type = String::class.asClassName().copy(nullable = true)) {
                initializer("description")
            }
            addProperty(
                name = "contentTypes",
                type = Set::class.asClassName().parameterizedBy(String::class.asClassName())
            ) {
                initializer("contentTypes")
            }
        }
    }

private val optionSerializerTypeSpec
    get() = buildClass("OptionSerializer") {
        val t = TypeVariableName("T")
        addTypeVariable(t)
        addSuperinterface(
            Poet.kSerializer.parameterizedBy(
                Poet.option.parameterizedBy(
                    t
                )
            )
        )
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

private val valueSerializerTypeSpec
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
            addStatement("val innerValue = decoder.decodeSerializableValue(inner)")
            addStatement("return wrap(innerValue)")
        }
    }
