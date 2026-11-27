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
            fun kind(name: String) = when (schemas.getValue(name)) {
                is Component.Schema.MapS -> "map"
                is Component.Schema.Unknown -> "untyped"
                is Component.Schema.Obj -> "class"
                else -> schemas.getValue(name)::class.simpleName
            }
            assertEquals(
                mapOf(
                    "ReachedOpen" to "map",
                    "FreeForm" to "untyped",
                    "Open" to "map",
                    "OpenSchema" to "map",
                    "Closed" to "untyped",
                    "Strict" to "class",
                    "WithExtras" to "untyped",
                    "WithTypedExtras" to "untyped",
                ),
                schemas.keys.filter { it != "Reached" }.associateWith(::kind),
                spec,
            )
            val reached = assertIs<Component.Schema.Obj>(schemas.getValue("Reached"), spec)
            assertIs<Component.Schema.MapS>(reached.properties.getValue("inlineOpen"), spec)
        }
    }
}
