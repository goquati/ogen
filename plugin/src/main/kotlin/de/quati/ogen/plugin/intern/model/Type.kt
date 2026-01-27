package de.quati.ogen.plugin.intern.model

import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import de.quati.kotlin.util.poet.PackageName

internal sealed interface Type : ToPoet {
    val packageName: PackageName
    val simpleNames: List<String>
    val parametrizedTypes: List<ToPoet>

    override val poet: TypeName
        get() = packageName.className(simpleNames)
            .let { className ->
                if (parametrizedTypes.isEmpty()) className
                else className.parameterizedBy(parametrizedTypes.map { it.poet })
            }

    fun nullable(value: Boolean) = TypeWithNullability(type = this, nullable = value)

    sealed interface NonPrimitiveType : Type {

        data class Option(
            val innerType: TypeWithNullability,
        ) : NonPrimitiveType {
            override val packageName = PackageName("de.quati.kotlin.util")
            override val simpleNames = listOf("Option")
            override val parametrizedTypes = listOf(innerType)
        }

        data class List(
            val innerType: Type,
        ) : NonPrimitiveType {
            override val packageName = PackageName("kotlin.collections")
            override val simpleNames = listOf("List")
            override val parametrizedTypes = listOf(innerType)

        }

        data class Flow(
            val innerType: Type,
        ) : NonPrimitiveType {
            override val packageName = PackageName("kotlinx.coroutines.flow")
            override val simpleNames = listOf("Flow")
            override val parametrizedTypes = listOf(innerType)
        }

        data class Map(
            val keyType: Type,
            val valueType: Type,
        ) : NonPrimitiveType {
            override val packageName = PackageName("kotlin.collections")
            override val simpleNames = listOf("Map")
            override val parametrizedTypes = listOf(keyType, valueType)
        }

        data class SerializableObject(
            override val packageName: PackageName,
            override val simpleNames: kotlin.collections.List<String>,
        ) : NonPrimitiveType {
            override val parametrizedTypes = emptyList<Type>()
        }

        data class Custom(
            override val packageName: PackageName,
            override val simpleNames: kotlin.collections.List<String>,
            val serializer: TypeName?,
        ) : NonPrimitiveType {
            override val parametrizedTypes = emptyList<Type>()
        }
    }

    enum class PrimitiveType(
        packageName: String,
        simpleName: String,
    ) : Type {
        String("kotlin", "String"),
        Int("kotlin", "Int"),
        Long("kotlin", "Long"),
        Float("kotlin", "Float"),
        Double("kotlin", "Double"),
        Boolean("kotlin", "Boolean"),
        Uuid("kotlin.uuid", "Uuid"),
        JsonElement("kotlinx.serialization.json", "JsonElement"),
        JsonObject("kotlinx.serialization.json", "JsonObject"),
        JsonNull("kotlinx.serialization.json", "JsonNull");

        override val packageName = PackageName(packageName)
        override val simpleNames = listOf(simpleName)
        override val parametrizedTypes = emptyList<Type>()
    }
}
