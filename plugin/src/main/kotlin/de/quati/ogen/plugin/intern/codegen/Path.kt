package de.quati.ogen.plugin.intern.codegen

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.buildCodeBlock
import de.quati.kotlin.util.poet.dsl.indent
import de.quati.ogen.plugin.intern.model.Endpoint

context(_: CodeGenContext)
internal fun List<Endpoint.StringableParameter>.toParameterMapCodeBlock(): CodeBlock {
    return if (isEmpty())
        CodeBlock.of("emptyMap()")
    else buildCodeBlock {
        add("mapOf(\n")
        indent {
            this@toParameterMapCodeBlock.forEach { param ->
                add("%S to %L.%L,\n", param.name, param.prettyName, param.toStringCodeBlock)
            }
        }
        add(")")
    }
}