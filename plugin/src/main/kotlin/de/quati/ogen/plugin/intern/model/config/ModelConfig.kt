package de.quati.ogen.plugin.intern.model.config

import de.quati.kotlin.util.poet.PackageName
import de.quati.ogen.plugin.intern.model.ComponentName
import de.quati.ogen.plugin.intern.model.Type
import de.quati.ogen.plugin.intern.model.TypeWithFormat


internal data class ModelConfig(
    val packageName: PackageName,
    val generate: Boolean,
    val typeMappings: Map<TypeWithFormat, Type.NonPrimitiveType.Custom>,
    val schemaMappings: Map<ComponentName.Schema, Type.NonPrimitiveType.Custom>,
    val postfix: String,
)