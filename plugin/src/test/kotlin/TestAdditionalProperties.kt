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
                schemas.filterKeys { it != "Reached" && "Patterns" !in it }.mapValues { it.value.kind() },
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
}
