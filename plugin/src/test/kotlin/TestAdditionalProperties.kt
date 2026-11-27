import de.quati.ogen.plugin.intern.model.Component
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** What `type: object` becomes depending on `additionalProperties`, the same in every version and place (#98, #108). */
class TestAdditionalProperties {
    private fun schemas(spec: String) =
        validatorConfigs(spec).specs.single().toSpec().components.schemas.mapKeys { it.key.toString() }

    @Test
    fun `additionalProperties has one meaning`() {
        for (spec in listOf("additional-properties", "additional-properties-3.1")) {
            val schemas = schemas(spec)
            fun kind(name: String) = when (val schema = schemas.getValue(name)) {
                is Component.Schema.MapS -> "map"
                is Component.Schema.Unknown -> "untyped"
                is Component.Schema.Obj -> when (val additional = schema.additionalProperties) {
                    null -> "class"
                    is Component.Schema.Unknown -> "class + untyped map"
                    is Component.Schema.Primitiv -> "class + map of ${additional.type.name.lowercase()}"
                    else -> "class + ${additional::class.simpleName}"
                }
                else -> schema::class.simpleName
            }
            assertEquals(
                mapOf(
                    "ReachedOpen" to "map",
                    "FreeForm" to "untyped",
                    "Open" to "map",
                    "OpenSchema" to "map",
                    "Closed" to "untyped",
                    "Strict" to "class",
                    "WithExtras" to "class + untyped map",
                    "WithTypedExtras" to "class + map of integer",
                ),
                schemas.keys.filter { it != "Reached" && "Patterns" !in it }.associateWith(::kind),
                spec,
            )
            val reached = assertIs<Component.Schema.Obj>(schemas.getValue("Reached"), spec)
            assertIs<Component.Schema.MapS>(reached.properties.getValue("inlineOpen"), spec)
        }
        // `patternProperties` (3.1) share the map with `additionalProperties`; one schema for all keeps its type.
        val schemas = schemas("additional-properties-3.1").filterKeys { "Patterns" in it }.mapValues { (_, schema) ->
            when (val additional = assertIs<Component.Schema.Obj>(schema).additionalProperties) {
                is Component.Schema.Primitiv -> additional.type.name.lowercase()
                else -> additional?.let { it::class.simpleName }
            }
        }
        assertEquals(
            mapOf(
                "StrictWithPatterns" to "string",
                "OpenWithPatterns" to "Inline",
                "SameWithPatterns" to "string",
                "MixedWithPatterns" to "Inline",
            ),
            schemas,
        )
    }
}
