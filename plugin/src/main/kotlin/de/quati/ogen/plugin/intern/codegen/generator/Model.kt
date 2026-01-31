package de.quati.ogen.plugin.intern.codegen.generator

import de.quati.ogen.plugin.intern.DirectorySyncService
import de.quati.ogen.plugin.intern.codegen.CodeGenContext
import de.quati.ogen.plugin.intern.codegen.toTypeSpec
import de.quati.ogen.plugin.intern.model.config.GeneratorConfig

context(c: CodeGenContext, d: DirectorySyncService)
internal fun GeneratorConfig.Model.sync() {
    c.spec.components.schemas.forEach { (_, schema) ->
        val typeSpec = schema.toTypeSpec() ?: return@forEach
        d.sync(fileName = "${schema.name.fileName}.kt") {
            addType(typeSpec)
        }
    }

    d.sync(fileName = "_utils.kt") {
        addType(c.buildSerializerTypeSpec())
    }
}