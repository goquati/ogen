package de.quati.ogen.plugin.intern.model

internal fun Component.Schema.flatten(): List<Component.Schema> = listOf(this) + when (this) {
    is Component.Schema.Composed -> listOf()
    is Component.Schema.SealedInterface -> schemas.values.flatMap { it.flatten() }
    is Component.Schema.Array -> items.flatten()
    is Component.Schema.Obj -> properties.values.flatMap { it.flatten() }
    is Component.Schema.MapS -> valueSchema.flatten()
    is Component.Schema.EnumString,
    Component.Schema.Null,
    is Component.Schema.PrimitivType,
    is Component.Schema.Ref,
    is Component.Schema.Unknown -> listOf()
}
