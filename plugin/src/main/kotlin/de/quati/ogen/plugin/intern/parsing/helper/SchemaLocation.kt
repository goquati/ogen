package de.quati.ogen.plugin.intern.parsing.helper

import de.quati.ogen.plugin.intern.model.ComponentName

internal sealed interface SchemaLocation {
    val name: ComponentName.Schema
    operator fun plus(name: String): SchemaLocation = Nested(this.name + name)
    fun updateLast(block: (String) -> String): SchemaLocation
    data class Root(
        override val name: ComponentName.Schema.Root,
        val type: Type,
    ) : SchemaLocation {
        enum class Type {
            COMPONENTS_SCHEMA,
            COMPONENTS_PARAMETER,
            PATH_BODY,
        }

        override fun updateLast(block: (String) -> String) = copy(name = name.updateLast(block))
    }

    @JvmInline
    value class Nested(
        override val name: ComponentName.Schema.Nested
    ) : SchemaLocation {
        override fun updateLast(block: (String) -> String) = Nested(name = name.updateLast(block))
    }
}