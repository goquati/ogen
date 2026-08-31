package de.quati.ogen.plugin.intern.model


internal sealed interface Component {
    sealed interface Schema : Component {
        val typeWithFormat: TypeWithFormat?
        val isNullable: Boolean
        val ref: RefString.Schema?

        sealed interface Inline : Schema
        sealed interface Value : NonInline {
            fun toInline(): Inline
        }
        sealed interface NonInline : Schema {
            val name: ComponentName.Schema
            override val ref get() = name.toRef()

            fun copySchema(newName: ComponentName.Schema) = when (this) {
                is Composed -> copy(name = newName)
                is EnumString -> copy(name = newName)
                is Obj -> copy(name = newName)
                is SealedInterface -> copy(name = newName)
                is Array.Value -> copy(name = newName)
                is MapS.Value -> copy(name = newName)
                is Unknown.Value -> copy(name = newName)
                is Primitiv.Value -> copy(name = newName)
            }
        }

        fun copySchema(isNullableUpdater: (Boolean) -> Boolean) = when (this) {
            is Composed -> copy(isNullable = isNullableUpdater(isNullable))
            is Array.Inline -> copy(isNullable = isNullableUpdater(isNullable))
            is Array.Value -> copy(isNullable = isNullableUpdater(isNullable))
            is EnumString -> copy(isNullable = isNullableUpdater(isNullable))
            is MapS.Inline -> copy(isNullable = isNullableUpdater(isNullable))
            is MapS.Value -> copy(isNullable = isNullableUpdater(isNullable))
            is Obj -> copy(isNullable = isNullableUpdater(isNullable))
            is SealedInterface -> copy(isNullable = isNullableUpdater(isNullable))
            is Primitiv.Inline -> copy(isNullable = isNullableUpdater(isNullable))
            is Primitiv.Value -> copy(isNullable = isNullableUpdater(isNullable))
            is Ref.Root -> copy(isNullable = isNullableUpdater(isNullable))
            is Ref.Nested -> copy(isNullable = isNullableUpdater(isNullable))
            is Unknown -> this
            Null -> this
        }

        data class EnumString(
            val values: List<String>,
            override val name: ComponentName.Schema,
            override val isNullable: Boolean,
            override val typeWithFormat: TypeWithFormat?,
        ) : NonInline

        sealed interface Primitiv : Schema {
            val type: Type
            val format: String?
            override val typeWithFormat get() = TypeWithFormat(type = type.name.lowercase(), format = format)

            data class Value(
                override val name: ComponentName.Schema,
                override val type: Type,
                override val format: String?,
                override val isNullable: Boolean,
            ) : Primitiv, Schema.Value {
                override fun toInline() = Inline(type, format, isNullable)
            }

            data class Inline(
                override val type: Type,
                override val format: String?,
                override val isNullable: Boolean,
            ) : Primitiv, Schema.Inline {
                override val ref = null
            }

            enum class Type {
                STRING, INTEGER, NUMBER, BOOLEAN
            }
        }

        sealed interface Unknown : Schema {
            data class Value(
                override val name: ComponentName.Schema,
                override val typeWithFormat: TypeWithFormat?,
            ) : Unknown, Schema.Value {
                override val isNullable: Boolean = false
                override fun toInline() = Inline(typeWithFormat)
            }

            data class Inline(
                override val typeWithFormat: TypeWithFormat?,
            ) : Unknown, Schema.Inline {
                override val isNullable: Boolean = false
                override val ref = null
            }
        }

        data class SealedInterface(
            override val name: ComponentName.Schema,
            override val isNullable: Boolean,
            val schemas: Map<String, Ref>,
            val discriminatorName: String,
            override val typeWithFormat: TypeWithFormat?,
        ) : NonInline


        data class Composed(
            override val name: ComponentName.Schema,
            val type: Type,
            override val isNullable: Boolean,
            val schemas: List<Inline>,
            override val typeWithFormat: TypeWithFormat?,
        ) : NonInline {
            enum class Type { AllOf, AnyOf }
        }

        sealed interface MapS : Schema {
            val valueSchema: Schema.Inline

            data class Value(
                override val name: ComponentName.Schema,
                override val valueSchema: Schema.Inline,
                override val isNullable: Boolean,
                override val typeWithFormat: TypeWithFormat?,
            ) : MapS, Schema.Value {
                override fun toInline() = Inline(valueSchema, isNullable, typeWithFormat)
            }

            data class Inline(
                override val valueSchema: Schema.Inline,
                override val isNullable: Boolean,
                override val typeWithFormat: TypeWithFormat?,
            ) : MapS, Schema.Inline {
                override val ref = null
            }
        }

        sealed interface Array : Schema {
            val items: Schema.Inline

            data class Value(
                override val name: ComponentName.Schema,
                override val items: Schema.Inline,
                override val isNullable: Boolean,
                override val typeWithFormat: TypeWithFormat?,
            ) : Array, Schema.Value {
                override fun toInline() = Inline(items, isNullable, typeWithFormat)
            }

            data class Inline(
                override val items: Schema.Inline,
                override val isNullable: Boolean,
                override val typeWithFormat: TypeWithFormat?,
            ) : Array, Schema.Inline {
                override val ref = null
            }
        }

        sealed interface Ref : Inline {
            val name: ComponentName.Schema

            companion object {
                fun create(
                    name: ComponentName.Schema,
                    isNullable: Boolean,
                ): Ref {
                    return when (name) {
                        is ComponentName.Schema.Root -> Root(name, isNullable)
                        is ComponentName.Schema.Nested -> Nested(name, isNullable)
                    }
                }
            }

            data class Root(
                override val name: ComponentName.Schema.Root,
                override val isNullable: Boolean,
            ) : Ref {
                override val ref = name.toRef()
                override val typeWithFormat = null
            }

            data class Nested(
                override val name: ComponentName.Schema.Nested,
                override val isNullable: Boolean,
            ) : Ref {
                override val ref = null
                override val typeWithFormat = null
            }
        }

        data class Obj(
            val required: Set<String>,
            val properties: Map<String, Inline>,
            override val name: ComponentName.Schema,
            override val isNullable: Boolean,
            override val typeWithFormat: TypeWithFormat?,
        ) : NonInline

        data object Null : Inline {
            override val isNullable = true
            override val typeWithFormat = null
            override val ref = null
        }
    }
}