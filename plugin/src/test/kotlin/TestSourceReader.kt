import de.quati.ogen.plugin.intern.oas.SourcePosition
import de.quati.ogen.plugin.intern.oas.read.ReadResult
import de.quati.ogen.plugin.intern.oas.read.SourceReader
import de.quati.ogen.plugin.intern.oas.ref.JsonPointer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestSourceReader {
    private fun read(text: String) = SourceReader.read(text, source = "spec.yaml")

    private fun readOk(text: String): JsonElement {
        val result = read(text)
        assertTrue(result.diagnostics.isEmpty(), result.diagnostics.joinToString("\n"))
        return assertNotNull(result.element)
    }

    private fun readFailing(text: String): String {
        val result = read(text)
        assertNull(result.element)
        return result.diagnostics.joinToString("\n")
    }

    private fun JsonElement.at(vararg path: String) = path.fold(this) { e, key -> e.jsonObject.getValue(key) }

    // --- JSON Pointer ---

    @Test
    fun `pointer escapes tilde and slash`() {
        val pointer = JsonPointer.ROOT / "paths" / "/users/{id}" / "a~b"
        assertEquals("/paths/~1users~1{id}/a~0b", pointer.toString())
        assertEquals(pointer, JsonPointer.parse(pointer.toString()))
        assertEquals(listOf("~1"), JsonPointer.parse("/~01").segments)
        assertEquals(JsonPointer.ROOT, JsonPointer.parse(""))
        assertEquals(listOf(""), JsonPointer.parse("/").segments)
        assertEquals(JsonPointer.of("a"), JsonPointer.of("a", "b").parent)
        assertNull(JsonPointer.ROOT.parent)
    }

    @Test
    fun `pointer rejects invalid input`() {
        assertFailsWith<IllegalArgumentException> { JsonPointer.parse("paths") }
        assertFailsWith<IllegalArgumentException> { JsonPointer.parse("/a~2") }
        assertFailsWith<IllegalArgumentException> { JsonPointer.parse("/a~") }
    }

    // --- YAML ---

    @Test
    fun `yaml keeps key order and positions`() {
        val result = read("openapi: 3.1.0\ninfo:\n  title: API\n  version: '1'\n")
        val element = assertNotNull(result.element)
        assertEquals(ReadResult.Format.YAML, result.format)
        assertEquals(listOf("openapi", "info"), element.jsonObject.keys.toList())
        assertEquals(SourcePosition(3, 10), result.positions[JsonPointer.of("info", "title")])
        assertEquals("spec.yaml:4:12 (#/info/version)", result.locationOf(JsonPointer.of("info", "version")).toString())
    }

    @Test
    fun `yaml duplicate keys are errors`() {
        val message = readFailing("a: 1\nb:\n  c: 1\n  c: 2\na: 3\n")
        assertContains(message, "spec.yaml:4:3 (#/b/c): duplicate key 'c' (first defined at 3:3)")
        assertContains(message, "spec.yaml:5:1 (#/a): duplicate key 'a' (first defined at 1:1)")
    }

    @Test
    fun `yaml scalars follow the 1_2 core schema`() {
        val e = readOk(
            """
            s1: '1.0'
            s2: yes
            s3: on
            s4: 2024-01-01
            b1: true
            b2: False
            n1: ~
            n2: null
            n3:
            i1: 42
            i2: +12
            i3: 007
            i4: 0o17
            i5: 0x1F
            i6: 123456789012345678901234567890
            f1: 1.0
            f2: .5
            f3: 1.
            f4: 1e3
            f5: 0.1000000000000000055511151231257827
            """.trimIndent()
        ).jsonObject
        fun s(key: String) = (e.getValue(key) as JsonPrimitive).let { it.isString to it.content }
        assertEquals(true to "1.0", s("s1"))
        assertEquals(true to "yes", s("s2"))
        assertEquals(true to "on", s("s3"))
        assertEquals(true to "2024-01-01", s("s4"))
        assertEquals(JsonPrimitive(true), e["b1"])
        assertEquals(JsonPrimitive(false), e["b2"])
        listOf("n1", "n2", "n3").forEach { assertEquals(JsonNull, e[it], it) }
        assertEquals(
            listOf("42", "12", "7", "15", "31", "123456789012345678901234567890"),
            (1..6).map { s("i$it").also { (isString, _) -> assertTrue(!isString) }.second },
        )
        assertEquals(
            listOf("1.0", "0.5", "1", "1e3", "0.1000000000000000055511151231257827"),
            (1..5).map { s("f$it").also { (isString, _) -> assertTrue(!isString) }.second },
        )
    }

    @Test
    fun `yaml infinity and nan are errors`() {
        assertContains(readFailing("a: .inf"), "spec.yaml:1:4 (#/a): '.inf' has no JSON representation")
        assertContains(readFailing("a: -.Inf"), "has no JSON representation")
        assertContains(readFailing("a: .NaN"), "has no JSON representation")
    }

    @Test
    fun `yaml non-string keys become strings`() {
        val e = readOk("200: ok\ntrue: yes\n1.5: x\n~: n\n")
        assertEquals(listOf("200", "true", "1.5", "~"), e.jsonObject.keys.toList())
    }

    @Test
    fun `yaml complex keys are errors`() {
        assertContains(readFailing("? [a, b]\n: x\n"), "invalid YAML: Non scalar key is detected")
    }

    @Test
    fun `yaml aliases are expanded`() {
        val e = readOk("base: &b\n  type: string\ncopy: *b\nlist: [*b, *b]\n")
        assertEquals(e.at("base"), e.at("copy"))
        assertEquals(JsonArray(listOf(e.at("base"), e.at("base"))), e.at("list"))
    }

    @Test
    fun `yaml recursive alias is an error`() {
        assertContains(readFailing("a: &a\n  b: *a\n"), "recursive YAML alias")
    }

    @Test
    fun `yaml merge keys are expanded`() {
        // Not part of YAML 1.2, but snakeyaml-engine supports them; keys of the mapping itself win.
        val e = readOk("base: &b {x: 1, y: 1}\nobj:\n  <<: *b\n  y: 2\n")
        assertEquals(mapOf("x" to "1", "y" to "2"), e.at("obj").jsonObject.mapValues { (it.value as JsonPrimitive).content })
    }

    @Test
    fun `yaml syntax errors name the position`() {
        val message = readFailing("a:\n  b: 1\n c: 2\n")
        assertTrue(message.startsWith("spec.yaml:3:2: invalid YAML:"), message)
    }

    @Test
    fun `yaml with several documents is an error`() {
        assertContains(readFailing("a: 1\n---\nb: 2\n"), "spec.yaml:3:1: the file contains 2 YAML documents")
    }

    @Test
    fun `empty yaml is an error`() {
        assertContains(readFailing(""), "the file is empty")
        assertContains(readFailing("# only a comment\n"), "the file is empty")
    }

    @Test
    fun `yaml with byte order mark`() {
        assertEquals(JsonObject(mapOf("a" to JsonPrimitive("b"))), readOk("\uFEFFa: b"))
    }

    // --- JSON ---

    @Test
    fun `json is detected by its first character`() {
        val result = read("\uFEFF \n  {\"openapi\": \"3.1.0\", \"n\": 1.0}")
        assertEquals(ReadResult.Format.JSON, result.format)
        val e = assertNotNull(result.element)
        assertEquals("1.0", (e.at("n") as JsonPrimitive).content)
        assertEquals(SourcePosition(2, 29), result.positions[JsonPointer.of("n")])
        // A top-level array is not a spec; it goes the YAML path, which reads it as well.
        assertEquals(ReadResult.Format.YAML, read("[1]").format)
    }

    @Test
    fun `json duplicate keys are errors`() {
        assertContains(readFailing("{\"a\": 1,\n \"a\": 2}"), "spec.yaml:2:2 (#/a): duplicate key 'a' (first defined at 1:2)")
    }

    @Test
    fun `invalid json is an error`() {
        assertContains(readFailing("{\"a\": 1\n \"b\": 2}"), "spec.yaml:2:5: invalid JSON: while parsing a flow mapping")
    }

    // --- files ---

    @Test
    fun `missing file is an error`() {
        val path = Files.createTempDirectory("ogen").resolve("missing.yaml")
        val result = SourceReader.read(path)
        assertNull(result.element)
        assertContains(result.diagnostics.single().toString(), "missing.yaml: cannot read file")
    }
}
