package de.quati.ogen.plugin.intern.model


internal sealed interface Component {
    sealed interface Schema : Component {
        val name: ComponentName.Schema
        val typeWithFormat: TypeWithFormat?
        val isNullable: Boolean

        fun copySchema(isNullableUpdater: (Boolean) -> Boolean) = when (this) {
            is Composed -> copy(isNullable = isNullableUpdater(isNullable))
            is Array -> copy(isNullable = isNullableUpdater(isNullable))
            is EnumString -> copy(isNullable = isNullableUpdater(isNullable))
            is MapS -> copy(isNullable = isNullableUpdater(isNullable))
            is Obj -> copy(isNullable = isNullableUpdater(isNullable))
            is SealedInterface -> copy(isNullable = isNullableUpdater(isNullable))
            is PrimitivType -> copy(isNullable = isNullableUpdater(isNullable))
            is Ref -> copy(isNullable = isNullableUpdater(isNullable))
            is Unknown -> this
            Null -> this
        }

        data class EnumString(
            val values: List<String>,
            override val name: ComponentName.Schema,
            override val isNullable: Boolean,
            override val typeWithFormat: TypeWithFormat?,
        ) : Schema

        data class PrimitivType(
            val type: Type,
            val format: String?,
            override val name: ComponentName.Schema,
            override val isNullable: Boolean,
        ) : Schema {
            override val typeWithFormat = TypeWithFormat(type = type.name.lowercase(), format = format)

            enum class Type {
                STRING, INTEGER, NUMBER, BOOLEAN
            }

        }

        data class Unknown(
            override val name: ComponentName.Schema,
            override val typeWithFormat: TypeWithFormat?,
        ) : Schema {
            override val isNullable: Boolean = false
        }

        data class SealedInterface(
            override val name: ComponentName.Schema,
            override val isNullable: Boolean,
            val schemas: Map<String, Ref>,
            val discriminatorName: String,
            override val typeWithFormat: TypeWithFormat?,
        ) : Schema {
            fun findChildSchema(childName: ComponentName.Schema): String? = schemas.entries
                .firstOrNull { (_, schema) -> schema.name == childName }?.key
        }


        data class Composed(
            override val name: ComponentName.Schema,
            val type: Type,
            override val isNullable: Boolean,
            val schemas: List<Schema>,
            override val typeWithFormat: TypeWithFormat?,
        ) : Schema {
            enum class Type { AllOf, AnyOf }
        }

        data class MapS(
            override val name: ComponentName.Schema,
            override val isNullable: Boolean,
            val valueSchema: Schema,
            override val typeWithFormat: TypeWithFormat?,
        ) : Schema

        data class Array(
            val items: Schema,
            override val name: ComponentName.Schema,
            override val isNullable: Boolean,
            override val typeWithFormat: TypeWithFormat?,
        ) : Schema

        data class Ref(
            val ref: RefString.Schema,
            override val name: ComponentName.Schema,
            override val isNullable: Boolean,
        ) : Schema {
            override val typeWithFormat = null
        }

        data class Obj(
            val required: Set<String>,
            val properties: Map<String, Schema>,
            override val name: ComponentName.Schema,
            override val isNullable: Boolean,
            override val typeWithFormat: TypeWithFormat?,
        ) : Schema

        data object Null : Schema {
            override val name = ComponentName.Schema.Unnamed
            override val isNullable = true
            override val typeWithFormat = null
        }
    }
}