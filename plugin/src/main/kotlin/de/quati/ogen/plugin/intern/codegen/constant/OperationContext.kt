package de.quati.ogen.plugin.intern.codegen.constant

import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.asClassName
import de.quati.kotlin.util.poet.PackageName
import de.quati.kotlin.util.poet.dsl.addClass
import de.quati.kotlin.util.poet.dsl.addProperty
import de.quati.kotlin.util.poet.dsl.buildInterface
import de.quati.kotlin.util.poet.dsl.primaryConstructor


internal fun operationContextTypeSpec(packageName: PackageName) = buildInterface("OperationContext") {
    addProperty(name = "name", type = String::class)
    addProperty(name = "description", type = String::class.asClassName().copy(nullable = true))
    addProperty(name = "deprecated", type = Boolean::class)
    addProperty(name = "tag", type = String::class.asClassName())
    addProperty(
        name = "security",
        type = Set::class.asClassName()
            .parameterizedBy(Set::class.asClassName().parameterizedBy(String::class.asClassName()))
    )
    val bodyTypeName = packageName.className("OperationContext", "Body")
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
