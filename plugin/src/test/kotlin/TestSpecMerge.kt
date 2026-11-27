import de.quati.ogen.plugin.intern.buildMergedSpec
import de.quati.ogen.plugin.intern.maxOasVersion
import de.quati.ogen.plugin.intern.model.config.InputConfig
import de.quati.ogen.plugin.intern.oas.read.SourceReader
import de.quati.ogen.plugin.intern.oas.write.JsonTreeWriter
import de.quati.ogen.plugin.intern.toJsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TestSpecMerge {
    private fun tempDir(): Path = Files.createTempDirectory("ogen-merge")

    @OptIn(ExperimentalPathApi::class)
    private fun copyOfOasDir(name: String): Path =
        Path("../tests/oas-dir/$name").copyToRecursively(tempDir().resolve(name), followLinks = false, overwrite = false)

    private fun merge(dir: Path, baseConfig: InputConfig.Merge.BaseConfig) =
        InputConfig.Merge(directoryPath = dir, mergeFileName = "merged", baseConfig = baseConfig).buildMergedSpec()

    private fun dataBase(additional: Map<String, Any?> = emptyMap()) = InputConfig.Merge.BaseConfig.Data(
        infoTitle = "MergedSpec",
        infoDescription = null,
        infoVersion = "1.0.0",
        additional = additional,
    )

    // --- the merges of tests/oas-dir, with the configuration of tests/oas-dir/build.gradle.kts ---

    @Test
    fun `oas1 merge is unchanged`() {
        val dir = copyOfOasDir("oas1")
        val merged = merge(dir, dataBase(additional = mapOf("security" to emptyList<String>())))
        assertEquals("merged.yaml", merged.name)
        assertEquals(Path("../tests/oas-dir/oas1/merged.yaml").readText(), merged.readText())
    }

    @Test
    fun `oas2 merge is unchanged`() {
        val dir = copyOfOasDir("oas2")
        val merged = merge(dir, InputConfig.Merge.BaseConfig.File(dir.resolve("base.yaml")))
        assertEquals(Path("../tests/oas-dir/oas2/merged.yaml").readText(), merged.readText())
    }

    // --- reading the directory ---

    @Test
    fun `files that are no spec are skipped`() {
        val dir = tempDir()
        dir.resolve("README.md").writeText("# Specs\n\nSome text: with a colon.\n")
        dir.resolve("broken.yaml").writeText("a: b\n c: d\n")
        dir.resolve("no-openapi.yaml").writeText("info: {title: x}\npaths:\n  /x: {}\n")
        dir.resolve("api.yaml").writeText("openapi: 3.0.3\npaths:\n  /a~b/{id}: {}\n  /c: {}\n")
        val merged = merge(dir, dataBase())
        assertEquals(
            """
            openapi: "3.0.3"
            info:
              title: "MergedSpec"
              version: "1.0.0"
            paths:
              /a~b/{id}:
                ${'$'}ref: "./api.yaml#/paths/~1a~0b~1{id}"
              /c:
                ${'$'}ref: "./api.yaml#/paths/~1c"

            """.trimIndent(),
            merged.readText(),
        )
    }

    @Test
    fun `a single json spec gives a json merge`() {
        val dir = tempDir()
        dir.resolve("api.json").writeText("""{"openapi": "3.1.0", "paths": {"/a": {}}}""")
        val merged = merge(dir, dataBase(additional = mapOf("x-n" to 1, "x-l" to listOf(true, null))))
        assertEquals("merged.json", merged.name)
        assertEquals(
            """{"openapi":"3.1.0","info":{"title":"MergedSpec","version":"1.0.0"},"x-n":1,"x-l":[true,null],""" +
                """"paths":{"/a":{"${'$'}ref":"./api.json#/paths/~1a"}}}""",
            merged.readText(),
        )
    }

    @Test
    fun `several json specs give a json merge`() {
        val dir = tempDir()
        dir.resolve("a.json").writeText("""{"openapi": "3.1.0", "paths": {"/a": {}}}""")
        dir.resolve("b.json").writeText("""{"openapi": "3.1.0", "paths": {"/b": {}}}""")
        assertEquals("merged.json", merge(dir, dataBase()).name)
    }

    @Test
    fun `mixed formats give a yaml merge`() {
        val dir = tempDir()
        dir.resolve("a.json").writeText("""{"openapi": "3.1.0", "paths": {"/a": {}}}""")
        dir.resolve("b.yaml").writeText("openapi: 3.1.0\npaths:\n  /b: {}\n")
        assertEquals("merged.yaml", merge(dir, dataBase()).name)
    }

    @Test
    fun `a directory without spec fails`() {
        val dir = tempDir().also { it.resolve("sub").createDirectories() }
        dir.resolve("README.md").writeText("text")
        val e = assertFailsWith<RuntimeException> { merge(dir, dataBase()) }
        assertContains(e.message!!, "doesn't contain valid specification")
    }

    @Test
    fun `invalid base spec fails`() {
        val dir = tempDir()
        dir.resolve("api.yaml").writeText("openapi: 3.0.3\npaths: {}\n")
        dir.resolve("base.yaml").writeText("a: 1\na: 2\n")
        val e = assertFailsWith<RuntimeException> { merge(dir, InputConfig.Merge.BaseConfig.File(dir.resolve("base.yaml"))) }
        assertContains(e.message!!, "duplicate key 'a'")
    }

    // --- helpers ---

    @Test
    fun `versions are compared as numbers`() {
        assertEquals("3.10.0", maxOasVersion(listOf("3.2.0", "3.10.0", "3.1.1")))
        assertEquals("3.1.0", maxOasVersion(listOf("3.0.4", "3.1.0", "3.1")))
        assertEquals(null, maxOasVersion(emptyList()))
    }

    private enum class Color { RED }

    @Test
    fun `dsl values become json`() {
        val value = mapOf(
            "s" to StringBuilder("text"), "i" to 1, "l" to 2L, "d" to 1.5, "b" to false, "n" to null,
            "e" to Color.RED, "list" to listOf(1, "a"), "array" to arrayOf("x"), "map" to mapOf(1 to "one"),
        )
        assertEquals(
            """{"s":"text","i":1,"l":2,"d":1.5,"b":false,"n":null,"e":"RED","list":[1,"a"],"array":["x"],"map":{"1":"one"}}""",
            JsonTreeWriter.toJson(value.toJsonElement("additional")),
        )
        val e = assertFailsWith<IllegalArgumentException> { mapOf("a" to listOf(Any())).toJsonElement("additional") }
        assertEquals("Value at 'additional.a[0]' has the unsupported type kotlin.Any", e.message)
        assertFailsWith<IllegalArgumentException> { Double.NaN.toJsonElement("x") }
    }

    // --- the writer writes what Jackson wrote (pinned in json-tree-writer/, see its README) ---

    private fun resource(name: String) = Path("src/test/resources/json-tree-writer/$name").readText()
    private val edgeCases: JsonElement by lazy { Json.parseToJsonElement(resource("input.json.txt")) }

    @Test
    fun `yaml output equals what jackson wrote`() {
        assertEquals(resource("expected.yaml.txt"), JsonTreeWriter.toYaml(edgeCases))
    }

    @Test
    fun `json output equals what jackson wrote`() {
        assertEquals(resource("expected.json.txt"), JsonTreeWriter.toJson(edgeCases))
    }

    @Test
    fun `line and paragraph separators are written as they are`() {
        // YAML 1.1 (Jackson) escapes them as \L and \P; YAML 1.2 treats them as printable. Both read back the same.
        val value = JsonObject(mapOf("s" to JsonPrimitive("a \u2028 b \u2029 c")))
        val yaml = JsonTreeWriter.toYaml(value)
        assertEquals("s: \"a \u2028 b \u2029 c\"\n", yaml)
        assertEquals(value, SourceReader.read(yaml, "merged.yaml").element)
    }
}
