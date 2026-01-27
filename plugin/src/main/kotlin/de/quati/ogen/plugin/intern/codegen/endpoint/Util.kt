package de.quati.ogen.plugin.intern.codegen.endpoint

import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import de.quati.kotlin.util.poet.dsl.addProperty
import de.quati.kotlin.util.poet.dsl.buildObject
import de.quati.kotlin.util.poet.dsl.indent
import de.quati.kotlin.util.poet.dsl.initializer
import de.quati.kotlin.util.poet.kotlinKeywords
import de.quati.kotlin.util.poet.makeDifferent
import de.quati.ogen.plugin.intern.DirectorySyncService
import de.quati.ogen.plugin.intern.codegen.CodeGenContext
import de.quati.ogen.plugin.intern.model.Endpoint
import de.quati.ogen.plugin.intern.model.config.GeneratorConfig
import kotlin.collections.plus
import kotlin.collections.toMutableSet

context(_: DirectorySyncService, _: CodeGenContext)
internal fun GeneratorConfig.syncEndpoints(): Unit = when (this) {
    is GeneratorConfig.ServerSpringV4 -> syncEndpoints()
}


internal class NameConflictResolver(forbidden: Iterable<String> = emptySet()) { // TODO move to quati util
    private val forbidden: MutableSet<String> = (kotlinKeywords + forbidden).toMutableSet()

    fun resolve(name: String): String = name.makeDifferent(forbidden).also {
        forbidden += it
    }
}

context(c: CodeGenContext)
internal fun Endpoint.generateOperationContextTypeSpec(
    block: TypeSpec.Builder.() -> Unit = {},
) = buildObject(name = operationNameContextName) {
    addSuperinterface(c.operationContext)
    addModifiers(KModifier.DATA)
    val request = requestBodyResolved
    val response = responseResolved
    val bodyTypeName = c.operationContext.nestedClass("Body")

    addProperty(name = "name", type = String::class.asClassName()) {
        addModifiers(KModifier.OVERRIDE)
        initializer("%S", operationName)
    }
    addProperty(name = "description", type = String::class.asClassName().copy(nullable = description == null)) {
        addModifiers(KModifier.OVERRIDE)
        if (description == null) initializer("null") else initializer("%S", description)
    }
    addProperty(name = "deprecated", type = Boolean::class.asClassName()) {
        addModifiers(KModifier.OVERRIDE)
        initializer("%L", deprecated)
    }
    addProperty(name = "tag", type = String::class.asClassName()) {
        addModifiers(KModifier.OVERRIDE)
        initializer("%S", tag)
    }

    addProperty(
        name = "security",
        type = Set::class.asClassName()
            .parameterizedBy(Set::class.asClassName().parameterizedBy(String::class.asClassName()))
    ) {
        addModifiers(KModifier.OVERRIDE)
        initializer {
            add("setOf(")
            security.data.forEachIndexed { i0, set ->
                add("%LsetOf(", if (i0 == 0) "" else ", ")
                set.forEachIndexed { i1, v ->
                    add("%L%S", if (i1 == 0) "" else ", ", v)
                }
                add(")")
            }
            add(")")
        }
    }
    addProperty(name = "defaultSuccessStatus", type = Int::class.asClassName()) {
        addModifiers(KModifier.OVERRIDE)
        initializer("%L", response.defaultSuccessStatus)
    }

    addProperty(name = "requestBody", type = bodyTypeName.copy(nullable = request == null)) {
        addModifiers(KModifier.OVERRIDE)
        if (request == null) initializer("null")
        else initializer {
            add("%T(\n", bodyTypeName)
            indent {
                add("description = %S,\n", request.description)
                add("contentTypes = setOf(")
                request.contentType?.values?.forEachIndexed { i, v ->
                    add("%L%S", if (i == 0) "" else ", ", v)
                }
                add("),\n")
            }
            add(")")
        }
    }


    addProperty(
        name = "responses",
        type = Map::class.asClassName().parameterizedBy(String::class.asClassName(), bodyTypeName),
    ) {
        addModifiers(KModifier.OVERRIDE)
        initializer {
            add("mapOf(")

            indent {
                response.data.forEach { (code, v) ->
                    add("%S to %T(\n", code, bodyTypeName)
                    indent {
                        add("description = %S,\n", v.description)
                        add("contentTypes = setOf(")
                        v.content.flatMap { it.contentType.values }.forEachIndexed { i, v ->
                            add("%L%S", if (i == 0) "" else ", ", v)
                        }
                        add("),\n")
                    }
                    add("),\n")
                }
            }

            add(")")
        }
    }
    block()
}
