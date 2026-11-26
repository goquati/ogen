import de.quati.ogen.plugin.intern.oas.Diagnostic
import de.quati.ogen.plugin.intern.oas.decode.StructureCheck
import de.quati.ogen.plugin.intern.oas.load.LoadedSpec
import de.quati.ogen.plugin.intern.oas.load.OasLoader
import de.quati.ogen.plugin.intern.oas.load.ResourceLoader
import de.quati.ogen.plugin.intern.oas.model.Parameter
import de.quati.ogen.plugin.intern.oas.model.RefOr
import de.quati.ogen.plugin.intern.oas.model.Schema
import de.quati.ogen.plugin.intern.oas.ref.JsonPointer
import de.quati.ogen.plugin.intern.oas.ref.RefTarget
import de.quati.ogen.plugin.intern.oas.ref.Resolver
import de.quati.ogen.plugin.intern.oas.ref.UriText
import de.quati.ogen.plugin.intern.oas.ref.at
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class TestOasLoader {
    private val header = "openapi: 3.1.0\ninfo: {title: API, version: '1'}\n"

    /** Documents by `mem:` URI; records what was requested. */
    private class Memory(vararg files: Pair<String, String>) : ResourceLoader {
        val files = files.associate { (name, text) -> URI("mem:/$name") to text }
        val requested = mutableListOf<String>()
        override fun load(uri: URI): ResourceLoader.Resource {
            requested += uri.toString()
            return files[uri]?.let { ResourceLoader.Resource.Text(it, uri.path.removePrefix("/")) }
                ?: ResourceLoader.Resource.Failure("no file $uri")
        }
    }

    private fun load(memory: Memory, root: String = "api/root.yaml") = OasLoader(memory).load(URI("mem:/$root"))

    private fun LoadedSpec.errors() = diagnostics.filter { it.severity == Diagnostic.Severity.ERROR }.map { it.toString() }

    private fun LoadedSpec.refAt(document: String, pointer: String) =
        documents.getValue(URI("mem:/$document")).element.at(JsonPointer.parse(pointer))!!.jsonPrimitive.content

    // --- references and URIs ---

    @Test
    fun `references are parsed against the base`() {
        val base = URI("file:/specs/api/root.yaml")
        assertEquals("file:/specs/common.yaml#/components/schemas/Id", RefTarget.parse("../common.yaml#/components/schemas/Id", base).toString())
        assertEquals("file:/specs/api/root.yaml#/components/schemas/Id", RefTarget.parse("#/components/schemas/Id", base).toString())
        assertEquals("file:/specs/api/other.yaml#", RefTarget.parse("other.yaml", base).toString())
        assertEquals("https://example.com/x.yaml#/a", RefTarget.parse("https://example.com/x.yaml#/a", base).toString())
        // Characters a URI does not allow, as in path names, are accepted; the fragment is percent-decoded.
        val braces = RefTarget.parse("./domain/users.yaml#/paths/~1users~1{id}", base)
        assertEquals(listOf("paths", "/users/{id}"), braces.pointer.segments)
        assertEquals("file:/specs/api/domain/users.yaml#/paths/~1users~1%7Bid%7D", braces.toString())
        assertEquals(listOf("a b", "ü", "100%"), RefTarget.parse("#/a%20b/%C3%BC/100%25", base).pointer.segments)
        assertEquals(listOf("50%"), RefTarget.parse("#/50%", base).pointer.segments)
        assertEquals(braces, RefTarget.parse(braces.toString(), base))
        assertEquals("file:/specs/my%20specs/a.yaml#", RefTarget.parse("../my specs/a.yaml", base).toString())
        val anchor = assertFailsWith<IllegalArgumentException> { RefTarget.parse("#pet", base) }
        assertContains(anchor.message!!, "only JSON Pointer fragments are supported")
    }

    @Test
    fun `percent decoding leaves invalid escapes alone`() {
        assertEquals("%zz %4", UriText.decode("%zz %4"))
        assertEquals("€", UriText.decode("%E2%82%AC"))
    }

    // --- loading ---

    @Test
    fun `references across files are made absolute`() {
        val memory = Memory(
            "api/root.yaml" to header + """
                paths:
                  /users/{id}:
                    ${'$'}ref: './paths/users.yaml#/paths/~1users~1{id}'
                components:
                  schemas:
                    User: {${'$'}ref: '../common.yaml#/components/schemas/User'}
            """.trimIndent(),
            "api/paths/users.yaml" to """
                paths:
                  /users/{id}:
                    get:
                      parameters:
                        - ${'$'}ref: '../../common.yaml#/components/parameters/Id'
                      responses:
                        '200':
                          description: ok
                          content:
                            application/json:
                              schema: {${'$'}ref: '../root.yaml#/components/schemas/User'}
            """.trimIndent(),
            "common.yaml" to """
                components:
                  parameters:
                    Id: {name: id, in: path, required: true, schema: {${'$'}ref: '#/components/schemas/Id'}}
                  schemas:
                    Id: {type: string}
                    User: {type: object, properties: {id: {${'$'}ref: '#/components/schemas/Id'}}}
                    Unused: {${'$'}ref: 'missing.yaml'}
            """.trimIndent(),
        )
        val spec = load(memory)
        assertEquals(emptyList(), spec.errors())
        assertEquals(setOf("mem:/api/root.yaml", "mem:/api/paths/users.yaml", "mem:/common.yaml"), spec.documents.keys.map { it.toString() }.toSet())
        assertEquals("mem:/api/paths/users.yaml#/paths/~1users~1%7Bid%7D", spec.refAt("api/root.yaml", "/paths/~1users~1{id}/\$ref"))
        assertEquals("mem:/common.yaml#/components/schemas/User", spec.refAt("api/root.yaml", "/components/schemas/User/\$ref"))
        assertEquals(
            "mem:/common.yaml#/components/parameters/Id",
            spec.refAt("api/paths/users.yaml", "/paths/~1users~1{id}/get/parameters/0/\$ref"),
        )
        assertEquals("mem:/common.yaml#/components/schemas/Id", spec.refAt("common.yaml", "/components/schemas/User/properties/id/\$ref"))
        // Only the referenced parts of a file are walked; 'Unused' and its broken reference are not.
        assertEquals("missing.yaml", spec.refAt("common.yaml", "/components/schemas/Unused/\$ref"))
        assertEquals(listOf("mem:/api/root.yaml", "mem:/api/paths/users.yaml", "mem:/common.yaml"), memory.requested)

        val resolver = Resolver(spec)
        val parameter = resolver.resolve(RefOr.Ref("mem:/common.yaml#/components/parameters/Id"), Parameter.serializer())
        assertEquals("id", assertNotNull(parameter.value).name)
        val idRef = assertIs<Schema.Obj>(parameter.value.schema).ref!!
        assertEquals(listOf("string"), assertIs<Schema.Obj>(resolver.resolve(idRef, Schema.serializer()).value).type)
    }

    @Test
    fun `files referencing each other are loaded once`() {
        val memory = Memory(
            "api/root.yaml" to header + "components:\n  schemas:\n    A: {\$ref: 'a.yaml#/A'}\n",
            "api/a.yaml" to "A: {properties: {b: {\$ref: 'b.yaml#/B'}}}\n",
            "api/b.yaml" to "B: {properties: {a: {\$ref: 'a.yaml#/A'}, self: {\$ref: '#/B'}}}\n",
        )
        val spec = load(memory)
        assertEquals(emptyList(), spec.errors())
        assertEquals(listOf("mem:/api/root.yaml", "mem:/api/a.yaml", "mem:/api/b.yaml"), memory.requested)
        assertEquals("mem:/api/b.yaml#/B", spec.refAt("api/b.yaml", "/B/properties/self/\$ref"))
    }

    @Test
    fun `recursive schemas stay references`() {
        val spec = load(Memory("api/root.yaml" to header + "components:\n  schemas:\n    Node: {properties: {next: {\$ref: '#/components/schemas/Node'}}}\n"))
        assertEquals(emptyList(), spec.errors())
        val node = Resolver(spec).resolve("mem:/api/root.yaml#/components/schemas/Node", Schema.serializer())
        val next = assertIs<Schema.Obj>(assertIs<Schema.Obj>(node.value).properties!!.getValue("next"))
        assertEquals("mem:/api/root.yaml#/components/schemas/Node", next.ref)
    }

    @Test
    fun `reference chains are followed and cycles reported`() {
        val spec = load(Memory("api/root.yaml" to header + """
            components:
              parameters:
                A: {${'$'}ref: '#/components/parameters/B'}
                B: {${'$'}ref: '#/components/parameters/C'}
                C: {name: c, in: query}
                X: {${'$'}ref: '#/components/parameters/Y'}
                Y: {${'$'}ref: '#/components/parameters/X'}
            """.trimIndent()))
        assertEquals(emptyList(), spec.errors())
        val resolver = Resolver(spec)
        assertEquals("c", resolver.resolve(RefOr.Ref("mem:/api/root.yaml#/components/parameters/A"), Parameter.serializer()).value?.name)
        val cycle = resolver.resolve(RefOr.Ref("mem:/api/root.yaml#/components/parameters/X"), Parameter.serializer())
        assertNull(cycle.value)
        assertContains(cycle.diagnostics.single().message, "references form a cycle")
    }

    @Test
    fun `unresolvable references are errors at the reference`() {
        val spec = load(Memory("api/root.yaml" to header + """
            components:
              schemas:
                A: {${'$'}ref: 'missing.yaml#/A'}
                B: {${'$'}ref: '#/components/schemas/Nope'}
                C: {${'$'}ref: '#anchor'}
                D: {${'$'}ref: 'https://example.com/d.yaml'}
            """.trimIndent()))
        assertEquals(
            listOf(
                "api/root.yaml:6:9 (#/components/schemas/B/\$ref): cannot resolve '#/components/schemas/Nope': '/components/schemas/Nope' does not exist in api/root.yaml",
                "api/root.yaml:7:9 (#/components/schemas/C/\$ref): cannot resolve '#anchor': '#anchor' names an anchor ('#anchor'); only JSON Pointer fragments are supported",
                "api/root.yaml:5:9 (#/components/schemas/A/\$ref): cannot resolve 'missing.yaml#/A': no file mem:/api/missing.yaml",
                "api/root.yaml:8:9 (#/components/schemas/D/\$ref): cannot resolve 'https://example.com/d.yaml': no file https://example.com/d.yaml",
            ),
            spec.errors(),
        )
    }

    @Test
    fun `remote references need an allowlist`() {
        val loader = ResourceLoader.default()
        val failure = assertIs<ResourceLoader.Resource.Failure>(loader.load(URI("https://example.com/a.yaml")))
        assertContains(failure.reason, "remote references are not loaded")
        val notAllowed = ResourceLoader.default(httpAllowlist = listOf("https://example.org/"))
        assertIs<ResourceLoader.Resource.Failure>(notAllowed.load(URI("https://example.com/a.yaml")))
        assertIs<ResourceLoader.Resource.Failure>(loader.load(URI("ftp://example.com/a.yaml")))
    }

    @Test
    fun `self sets the base, files are still loaded from where the document lies`() {
        val memory = Memory(
            "local/root.yaml" to "openapi: 3.2.0\n\$self: https://example.com/api/openapi.yaml\ninfo: {title: API, version: '1'}\n" +
                "components:\n  schemas:\n    A: {\$ref: 'schemas.yaml#/S'}\n",
            "local/schemas.yaml" to "S: {type: string}\n",
        )
        val spec = OasLoader(memory).load(URI("mem:/local/root.yaml"))
        assertEquals(emptyList(), spec.errors())
        assertEquals("https://example.com/api/schemas.yaml#/S", spec.refAt("local/root.yaml", "/components/schemas/A/\$ref"))
        assertEquals(listOf("mem:/local/root.yaml", "mem:/local/schemas.yaml"), memory.requested)
        assertEquals(JsonPrimitive("string"), Resolver(spec).element("https://example.com/api/schemas.yaml#/S")!!.jsonObject["type"])
    }

    @Test
    fun `schema ids set the base and identify the schema`() {
        val spec = load(Memory("api/root.yaml" to header + """
            components:
              schemas:
                Pet:
                  ${'$'}id: https://example.com/schemas/pet
                  properties:
                    owner: {${'$'}ref: '#/${'$'}defs/Owner'}
                  ${'$'}defs:
                    Owner: {type: string}
                Other: {${'$'}ref: 'https://example.com/schemas/pet#/${'$'}defs/Owner'}
            """.trimIndent()))
        assertEquals(emptyList(), spec.errors())
        assertEquals("https://example.com/schemas/pet#/\$defs/Owner", spec.refAt("api/root.yaml", "/components/schemas/Pet/properties/owner/\$ref"))
        val owner = Resolver(spec).resolve(spec.refAt("api/root.yaml", "/components/schemas/Other/\$ref"), Schema.serializer())
        assertEquals(listOf("string"), assertIs<Schema.Obj>(owner.value).type)
    }

    @Test
    fun `data is not walked, discriminator mappings are`() {
        val spec = load(Memory("api/root.yaml" to header + """
            x-data: {${'$'}ref: 'nope.yaml'}
            components:
              schemas:
                Pet:
                  example: {${'$'}ref: 'nope.yaml'}
                  default: {${'$'}ref: 'nope.yaml'}
                  enum: [{${'$'}ref: 'nope.yaml'}]
                  oneOf: [{${'$'}ref: '#/components/schemas/Cat'}]
                  discriminator:
                    propertyName: kind
                    mapping: {cat: '#/components/schemas/Cat', dog: Dog}
                  if: {${'$'}ref: '#/components/schemas/Cat'}
                Cat: {type: object}
            """.trimIndent()))
        assertEquals(emptyList(), spec.errors())
        val pet = spec.root!!.element.at(JsonPointer.parse("/components/schemas/Pet"))!!.jsonObject
        assertEquals("nope.yaml", pet["example"]!!.jsonObject["\$ref"]!!.jsonPrimitive.content)
        assertEquals("mem:/api/root.yaml#/components/schemas/Cat", pet["discriminator"]!!.jsonObject["mapping"]!!.jsonObject["cat"]!!.jsonPrimitive.content)
        assertEquals("Dog", pet["discriminator"]!!.jsonObject["mapping"]!!.jsonObject["dog"]!!.jsonPrimitive.content)
        assertEquals("mem:/api/root.yaml#/components/schemas/Cat", pet["if"]!!.jsonObject["\$ref"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a root that cannot be loaded is an error`() {
        val spec = load(Memory(), root = "api/none.yaml")
        assertNull(spec.root)
        assertEquals(listOf("mem:/api/none.yaml: cannot load the spec: no file mem:/api/none.yaml"), spec.errors())
    }

    // --- the specs of the repository ---

    @Test
    fun `merged specs of tests oas-dir load all their files`() {
        listOf("oas1", "oas2").forEach { dir ->
            val spec = OasLoader().load(Path("../tests/oas-dir/$dir/merged.yaml").absolute().toUri())
            assertEquals(emptyList(), spec.errors(), dir)
            val names = spec.documents.keys.map { Path(it.path).name }.toSet()
            assertTrue("common.yaml" in names && names.size >= 3, "$dir loaded $names")
            spec.refs.forEach { site -> assertTrue(site.target.toString().startsWith("file:/"), site.target.toString()) }
        }
    }

    @Test
    fun `every reference in the repository specs resolves to its expected type`() {
        val problems = mutableListOf<String>()
        var references = 0
        for (path in repositorySpecFiles()) {
            val element = de.quati.ogen.plugin.intern.oas.read.SourceReader.read(path).element as? JsonObject ?: continue
            if ("openapi" !in element) continue
            val spec = OasLoader().load(path.absolute().toUri())
            problems += spec.errors().map { "$path: $it" }
            for (site in spec.refs) {
                val target = site.target ?: continue
                references++
                val located = spec.locate(target) ?: continue
                val document = spec.documents.getValue(located.document)
                val value = document.element.at(located.pointer) ?: continue
                StructureCheck(document::locationOf).check(value, site.expected, located.pointer)
                    .filter { it.severity == Diagnostic.Severity.ERROR }
                    .forEach { problems += "$path: ${site.ref}: $it" }
            }
        }
        println("Resolved $references references")
        assertTrue(references > 100, "only $references references")
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }
}
