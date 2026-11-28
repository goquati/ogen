import de.quati.ogen.plugin.intern.model.Component
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** What `type: object` becomes depending on `additionalProperties`, the same in every version and place (#98, #108). */
class TestAdditionalProperties {
    private fun schemas(spec: String) =
        validatorConfigs(spec).specs.single().toSpec().components.schemas.mapKeys { it.key.toString() }

    private fun Component.Schema.Inline.kind() = when (this) {
        is Component.Schema.Unknown -> "untyped"
        is Component.Schema.Primitiv -> type.name.lowercase()
        else -> this::class.simpleName
    }

    private fun Component.Schema.kind() = when (this) {
        is Component.Schema.MapS -> "map of ${valueSchema.kind()}"
        is Component.Schema.Unknown -> "untyped"
        is Component.Schema.Obj -> additionalProperties?.let { "class + map of ${it.kind()}" } ?: "class"
        else -> this::class.simpleName
    }

    @Test
    fun `additionalProperties has one meaning`() {
        for (spec in listOf("additional-properties", "additional-properties-3.1")) {
            val schemas = schemas(spec)
            assertEquals(
                mapOf(
                    "ReachedOpen" to "map of untyped",
                    "FreeForm" to "untyped",
                    "Open" to "map of untyped",
                    "OpenSchema" to "map of untyped",
                    "Closed" to "untyped",
                    "Strict" to "class",
                    "WithExtras" to "class + map of untyped",
                    "WithTypedExtras" to "class + map of integer",
                ),
                schemas.filterKeys { it != "Reached" && "Patterns" !in it && !it.startsWith("RequiredExtra") }.mapValues { it.value.kind() },
                spec,
            )
            val reached = assertIs<Component.Schema.Obj>(schemas.getValue("Reached"), spec)
            assertIs<Component.Schema.MapS>(reached.properties.getValue("inlineOpen"), spec)
        }
    }

    @Test
    fun `patternProperties share the map with additionalProperties`() {
        // One schema for all keeps its type; a pattern with `false` allows no key.
        assertEquals(
            mapOf(
                "StrictWithPatterns" to "class + map of string",
                "OpenWithPatterns" to "class + map of untyped",
                "SameWithPatterns" to "class + map of string",
                "MixedWithPatterns" to "class + map of untyped",
                "ForbiddenWithPatterns" to "class",
                "MapWithPatterns" to "map of string",
                "OpenMapWithPatterns" to "map of untyped",
                "MixedMapWithPatterns" to "map of untyped",
            ),
            schemas("additional-properties-3.1").filterKeys { "Patterns" in it }.mapValues { it.value.kind() },
        )
    }

    @Test
    fun `required keys that properties don't declare become properties`() {
        val schemas = schemas("additional-properties-3.1")
        fun obj(name: String) = assertIs<Component.Schema.Obj>(schemas.getValue(name), name)
        fun Component.Schema.Obj.shape() = properties.mapValues { (key, value) ->
            value.kind() + if (key in required) "" else "?"
        } to additionalProperties?.kind()

        assertEquals(mapOf("id" to "string?", "tenant" to "string") to "string", obj("RequiredExtra").shape())
        assertEquals(mapOf("tenant" to "integer") to "integer", obj("RequiredExtraWithoutProperties").shape())
        // `true` and `{}` leave the value untyped.
        assertEquals(mapOf("id" to "string?", "tenant" to "untyped") to "untyped", obj("RequiredExtraOpen").shape())
        assertEquals(mapOf("id" to "string?", "tenant" to "untyped") to "untyped", obj("RequiredExtraOpenSchema").shape())
        // An object schema gives the property the class of the map's values, not a copy of it.
        val inline = obj("RequiredExtraInlineObject")
        assertIs<Component.Schema.Ref.Nested>(inline.additionalProperties)
        assertEquals(inline.additionalProperties, inline.properties.getValue("tenant"))
        val ref = obj("RequiredExtraRef")
        assertEquals(ref.additionalProperties, ref.properties.getValue("tenant"))
        assertEquals(setOf("tenant"), ref.required)
        assertEquals(
            mapOf("id" to "string?", "x-tenant" to "string", "other" to "integer") to "untyped",
            obj("RequiredExtraMatchingPattern").shape(),
        )
        // Several matching patterns with different schemas leave the value untyped.
        assertEquals(mapOf("x-n-tenant" to "untyped") to "untyped", obj("RequiredExtraTwoMatches").shape())
        assertEquals(mapOf("tenant" to "untyped") to "untyped", obj("RequiredExtraUnmatchedPattern").shape())
        // A key nothing allows stays undeclared; the validator reports it.
        assertEquals(mapOf("id" to "string?") to null, obj("RequiredExtraForbidden").shape())
        assertEquals(mapOf("id" to "string?") to null, obj("RequiredExtraOnlyForbidden").shape())
        val composed = assertIs<Component.Schema.Composed>(schemas.getValue("RequiredExtraComposed"))
        assertEquals("untyped", composed.schemas[1].kind())
    }
}
