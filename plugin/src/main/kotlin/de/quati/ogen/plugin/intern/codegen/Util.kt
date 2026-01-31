package de.quati.ogen.plugin.intern.codegen

import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import de.quati.kotlin.util.poet.dsl.addParameter
import de.quati.kotlin.util.poet.dsl.addProperty


context(t: TypeSpec.Builder)// TODO move to quati util
internal fun FunSpec.Builder.addConstructorProperty(
    name: String,
    type: TypeName,
    modifiers: List<KModifier> = emptyList(),
    block: (param: ParameterSpec.Builder, prop: PropertySpec.Builder) -> Unit = { _, _ -> },
) {
    addParameter(name, type) {
        addModifiers(modifiers)
        t.addProperty(name, type) {
            initializer(name)
            block(this@addParameter, this)
        }
    }
}
