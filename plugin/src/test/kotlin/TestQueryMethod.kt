import de.quati.ogen.plugin.EndpointInfo
import de.quati.ogen.plugin.SpecsConfigBuilder
import de.quati.ogen.plugin.intern.oas.OasParseResult
import de.quati.ogen.plugin.intern.oas.OasParser
import de.quati.ogen.plugin.intern.oas.load.ResourceLoader
import de.quati.ogen.plugin.intern.tasks.Generator
import org.slf4j.LoggerFactory
import java.net.URI
import kotlin.io.path.createTempDirectory
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The QUERY method of OpenAPI 3.2 (#119). */
class TestQueryMethod {
    private fun generate(endpoints: MutableList<EndpointInfo>): Map<String, String> {
        val configs = SpecsConfigBuilder().apply {
            utilPackageName("de.quati.ogen.test.gen.util")
            add(packageName = "de.quati.ogen.test.gen") {
                specFile(TestQueryMethod::class.java.getResource("query-method.yaml")!!.path)
                operationIdGenerator { endpoints += it; "search" }
                model {}
                clientKtor {}
                serverSpringV4 {}
            }
        }.build()
        val dir = createTempDirectory("test-query-method")
        try {
            Generator(rootOutputDir = dir, logger = LoggerFactory.getLogger(TestQueryMethod::class.java)).generate(configs = configs)
            return dir.walk().associate { it.toString().removePrefix(dir.toString()) to it.readText() }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a QUERY operation is generated for client and server`() {
        val endpoints = mutableListOf<EndpointInfo>()
        val files = generate(endpoints)
        assertEquals(listOf(EndpointInfo.Method.QUERY), endpoints.map { it.method }.distinct())
        val client = files.entries.single { "/client/" in it.key && it.key.endsWith("SearchApi.kt") }.value
        assertContains(client, "this.method = HttpMethod(\"QUERY\")")
        assertContains(client, "suspend fun search(")
        val server = files.entries.single { "/server/" in it.key && it.key.endsWith("SearchApi.kt") }.value
        assertContains(server, "method = [RequestMethod.QUERY]")
        assertContains(server, "consumes = [\"application/json\"]")
    }

    private fun parse(version: String): OasParseResult = OasParser.parse(URI("mem:/spec.yaml")) {
        ResourceLoader.Resource.Text(
            "openapi: $version\ninfo: {title: t, version: '1'}\npaths:\n  /search:\n" +
                "    query: {responses: {'200': {description: ok}}}\n",
            "spec.yaml",
        )
    }

    @Test
    fun `query is a field of the Path Item only in OpenAPI 3_2`() {
        val v32 = parse("3.2.0")
        assertEquals(emptyList(), v32.errors)
        assertNotNull(v32.openApi!!.paths!!.pathItems.getValue("/search").query)
        for ((version, minor) in listOf("3.1.0" to "3.1", "3.0.3" to "3.0")) {
            val old = parse(version)
            assertEquals(
                listOf("spec.yaml:5:5 (#/paths/~1search/query): unknown field 'query' in PathItem of OpenAPI $minor (added in 3.2)"),
                old.errors.map { it.toString() },
            )
            assertNull(old.openApi!!.paths!!.pathItems.getValue("/search").query)
        }
    }

    @Test
    fun `additionalOperations is a field of the Path Item only in OpenAPI 3_2`() {
        val old = OasParser.parse(URI("mem:/spec.yaml")) {
            ResourceLoader.Resource.Text(
                "openapi: 3.1.0\ninfo: {title: t, version: '1'}\npaths:\n  /search:\n" +
                    "    additionalOperations: {LINK: {responses: {'200': {description: ok}}}}\n",
                "spec.yaml",
            )
        }
        assertEquals(
            listOf("spec.yaml:5:5 (#/paths/~1search/additionalOperations): unknown field 'additionalOperations' in PathItem of OpenAPI 3.1 (added in 3.2)"),
            old.errors.map { it.toString() },
        )
        assertNull(old.openApi!!.paths!!.pathItems.getValue("/search").additionalOperations)
    }
}
