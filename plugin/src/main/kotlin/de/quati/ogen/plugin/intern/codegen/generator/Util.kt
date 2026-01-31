package de.quati.ogen.plugin.intern.codegen.generator

import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.buildCodeBlock
import de.quati.kotlin.util.poet.dsl.addProperty
import de.quati.kotlin.util.poet.dsl.buildObject
import de.quati.kotlin.util.poet.dsl.indent
import de.quati.kotlin.util.poet.dsl.initializer
import de.quati.kotlin.util.poet.kotlinKeywords
import de.quati.kotlin.util.poet.makeDifferent
import de.quati.ogen.plugin.intern.codegen.CodeGenContext
import de.quati.ogen.plugin.intern.model.Endpoint
import de.quati.ogen.plugin.intern.model.Security
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.security.SecurityScheme
import kotlin.collections.plus
import kotlin.collections.toMutableSet

internal class NameConflictResolver(forbidden: Iterable<String> = emptySet()) { // TODO move to quati util
    private val forbidden: MutableSet<String> = (kotlinKeywords + forbidden).toMutableSet()

    fun resolve(name: String): String = name.makeDifferent(forbidden).also {
        forbidden += it
    }
}

internal val SecurityScheme.Type.prettyName get() = toString().replaceFirstChar(Char::titlecase)

internal val PathItem.HttpMethod.ktorName get() = when (this) {
    PathItem.HttpMethod.POST -> "Post"
    PathItem.HttpMethod.GET -> "Get"
    PathItem.HttpMethod.PUT -> "Put"
    PathItem.HttpMethod.DELETE -> "Delete"
    PathItem.HttpMethod.HEAD -> "Head"
    PathItem.HttpMethod.OPTIONS -> "Options"
    PathItem.HttpMethod.TRACE -> "Trace"
    PathItem.HttpMethod.PATCH -> "Patch"
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
        type = List::class.asClassName().parameterizedBy(c.specConfig.sharedConfig.securityRequirement)
    ) {
        addModifiers(KModifier.OVERRIDE)
        initializer(securityRequirementListCodeBlock(security))
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


context(c: CodeGenContext)
internal fun securityRequirementListCodeBlock(security: Security) = buildCodeBlock {
    if (security.data.isEmpty()){
        add("emptyList()")
        return@buildCodeBlock
    }

    add("listOf(\n")
    security.data.forEach { set ->
        add(
            "%T(listOf(",
            c.specConfig.sharedConfig.securityRequirement,
        )
        set.forEachIndexed { i1, v ->
            add(
                "%L%T.%L(name = %S)",
                if (i1 == 0) "" else ", ",
                c.specConfig.sharedConfig.securityRequirementObject,
                v.type.prettyName,
                v.name,
            )
        }
        add(")),\n")
    }
    add(")")
}
