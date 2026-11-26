import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import kotlin.test.Test
import kotlin.test.assertEquals

/** Phase 0 smoke test: the serialization compiler plugin and the parser libraries work together. */
class TestOasDependencies {
    @Serializable
    private data class Info(val title: String, val version: String)

    @Test
    fun `yaml is read and decoded`() {
        val yaml = Load(LoadSettings.builder().setAllowDuplicateKeys(false).build())
            .loadFromString("title: API\nversion: '1.0'") as Map<*, *>
        val json = Json.encodeToString(yaml.mapKeys { it.key.toString() }.mapValues { it.value.toString() })
        assertEquals(Info(title = "API", version = "1.0"), Json.decodeFromString<Info>(json))
    }
}
