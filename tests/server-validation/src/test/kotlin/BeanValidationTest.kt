import de.quati.ogen.BaseApplication
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.Test

@SpringBootTest(
    classes = [BaseApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class BeanValidationTest {
    @LocalServerPort
    private var port: Int = 0
    private val client by lazy { WebTestClient.bindToServer().baseUrl("http://localhost:$port").build() }

    private fun getItem(
        path: String = "abc",
        query: String = "",
        headers: Map<String, String> = emptyMap(),
        expectedStatus: Int,
        expectedBody: String? = null,
    ) {
        val result = client.get().uri("/api/items/$path?$query")
            .headers { h -> headers.forEach { (name, value) -> h.add(name, value) } }
            .exchange()
            .expectStatus().isEqualTo(expectedStatus)
            .expectBody().returnResult()
        if (expectedBody != null)
            result.responseBody?.decodeToString() shouldBe expectedBody
    }

    private fun post(url: String, body: String, expectedStatus: Int) {
        client.post().uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isEqualTo(expectedStatus)
    }

    private fun item(vararg overrides: Pair<String, String?>): String {
        val fields = linkedMapOf(
            "name" to "\"foo\"",
            "price" to "1.5",
            "code" to "\"ABC\"",
            "children" to "[{\"value\":\"a\"}]",
            "childList" to "[{\"value\":\"a\"}]",
        )
        overrides.forEach { (key, value) -> if (value == null) fields.remove(key) else fields[key] = value }
        return fields.entries.joinToString(",", prefix = "{", postfix = "}") { (k, v) -> "\"$k\":$v" }
    }

    @Test
    fun `valid parameters`() = getItem(
        path = "abcde",
        query = "limit=100&offset=999&score=1.5&search=xfoox&tags=a,b&code=XYZ",
        headers = mapOf("x-request-id" to "123"),
        expectedStatus = 200,
        expectedBody = "abcde|100|999|1.5|xfoox|[a, b]|XYZ|123",
    )

    @Test
    fun `path parameter`() {
        getItem(path = "a", expectedStatus = 400) // minLength
        getItem(path = "abcdef", expectedStatus = 400) // maxLength
        getItem(path = "ABC", expectedStatus = 400) // pattern
    }

    @Test
    fun `integer parameters`() {
        getItem(query = "limit=1", expectedStatus = 200)
        getItem(query = "limit=0", expectedStatus = 400)
        getItem(query = "limit=101", expectedStatus = 400)
        getItem(query = "offset=-5", expectedStatus = 200)
        getItem(query = "offset=1000", expectedStatus = 400) // exclusiveMaximum
    }

    @Test
    fun `number parameter`() {
        getItem(query = "score=0.001", expectedStatus = 200)
        getItem(query = "score=0", expectedStatus = 400) // exclusiveMinimum
        getItem(query = "score=1.51", expectedStatus = 400)
    }

    @Test
    fun `pattern without anchors matches anywhere`() {
        getItem(query = "search=foo", expectedStatus = 200)
        getItem(query = "search=bar", expectedStatus = 400)
    }

    @Test
    fun `array parameter`() = getItem(query = "tags=a,b,c", expectedStatus = 400)

    @Test
    fun `value class parameter`() = getItem(query = "code=abc", expectedStatus = 400)

    @Test
    fun `header parameter`() = getItem(headers = mapOf("x-request-id" to "12"), expectedStatus = 400)

    private fun getEdgeCases(query: String, expectedStatus: Int) {
        client.get().uri("/api/edge-cases?$query").exchange().expectStatus().isEqualTo(expectedStatus)
    }

    @Test
    fun `pattern with top-level alternation`() {
        getEdgeCases("startOrEnd=fooXYZ", expectedStatus = 204)
        getEdgeCases("startOrEnd=XYZbar", expectedStatus = 204)
        getEdgeCases("startOrEnd=XYZ", expectedStatus = 400)
    }

    @Test
    fun `integer bounds at and beyond the range of long`() {
        getEdgeCases("smallest=-9223372036854775808", expectedStatus = 204)
        getEdgeCases("beyondLong=9223372036854775807", expectedStatus = 400)
    }

    @Test
    fun `reference to a type-mapped schema`() {
        getEdgeCases("amount=0", expectedStatus = 204)
        getEdgeCases("amount=-0.01", expectedStatus = 400)
    }

    @Test
    fun `items of a query parameter`() {
        getEdgeCases("words=abc,de", expectedStatus = 204)
        getEdgeCases("words=abc,defg", expectedStatus = 400)
    }

    @Test
    fun `items and values of body properties`() {
        post(
            "/api/items",
            item(
                "labels" to "[\"abc\"]",
                "scores" to "{\"a\":10}",
                "matrix" to "[[\"ab\",\"c\"]]",
                "codes" to "[\"ABC\"]",
            ),
            expectedStatus = 200,
        )
        post("/api/items", item("labels" to "[\"abc\",\"abcd\"]"), expectedStatus = 400) // optional list
        post("/api/items", item("scores" to "{\"a\":11}"), expectedStatus = 400) // map value
        post("/api/items", item("matrix" to "[[\"a\",\"b\",\"c\"]]"), expectedStatus = 400) // nested maxItems
        post("/api/items", item("matrix" to "[[\"A\"]]"), expectedStatus = 400) // nested pattern
        post("/api/items", item("codes" to "[\"abc\"]"), expectedStatus = 400) // value class items
        post("/api/items", item("optionalChildren" to "[{\"value\":\"abcd\"}]"), expectedStatus = 400)
        post("/api/items", item("childList" to "[{\"value\":\"abcd\"}]"), expectedStatus = 400) // unboxed
        post("/api/items", item("optionalChildList" to "[{\"value\":\"abcd\"}]"), expectedStatus = 400) // boxed
        post(
            "/api/items",
            item("optionalChildren" to "[{\"value\":\"a\"}]", "optionalChildList" to "[{\"value\":\"a\"}]"),
            expectedStatus = 200,
        )
    }

    @Test
    fun `valid body`() {
        post("/api/items", item(), expectedStatus = 200)
        post(
            "/api/items",
            item(
                "email" to "\"foo@bar.de\"",
                "age" to "150",
                "tags" to "[\"a\",\"b\",\"c\"]",
                "attributes" to "{\"a\":\"b\",\"c\":\"d\"}",
                "otherCode" to "\"XYZ\"",
                "percent" to "100",
                "child" to "{\"value\":\"abc\"}",
            ),
            expectedStatus = 200,
        )
    }

    @Test
    fun `required body properties`() {
        post("/api/items", item("name" to "\"f\""), expectedStatus = 400)
        post("/api/items", item("name" to "\"foobarfoobar\""), expectedStatus = 400)
        post("/api/items", item("price" to "0"), expectedStatus = 400)
        post("/api/items", item("code" to "\"abc\""), expectedStatus = 400)
        post("/api/items", item("children" to "[{\"value\":\"abcd\"}]"), expectedStatus = 400)
    }

    @Test
    fun `optional body properties`() {
        post("/api/items", item("email" to "\"no-email\""), expectedStatus = 400)
        post("/api/items", item("age" to "-1"), expectedStatus = 400)
        post("/api/items", item("age" to "151"), expectedStatus = 400)
        post("/api/items", item("tags" to "[\"a\",\"b\",\"c\",\"d\"]"), expectedStatus = 400)
        post("/api/items", item("attributes" to "{\"a\":\"b\",\"c\":\"d\",\"e\":\"f\"}"), expectedStatus = 400)
        post("/api/items", item("otherCode" to "\"abc\""), expectedStatus = 400)
        post("/api/items", item("percent" to "101"), expectedStatus = 400)
        post("/api/items", item("child" to "{\"value\":\"abcd\"}"), expectedStatus = 400)
    }

    @Test
    fun `array body`() {
        post("/api/items/batch", "[${item()}]", expectedStatus = 200)
        post("/api/items/batch", "[]", expectedStatus = 400) // minItems
        post("/api/items/batch", "[${item()},${item()},${item()}]", expectedStatus = 400) // maxItems
        post("/api/items/batch", "[${item("name" to "\"f\"")}]", expectedStatus = 400) // cascade into items
    }
}
