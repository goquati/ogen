import de.quati.ogen.BaseApplication
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
class ApiResponseTest {
    @LocalServerPort
    private var port: Int = 0
    private val client by lazy { WebTestClient.bindToServer().baseUrl("http://localhost:$port").build() }

    @Test
    fun `single response with flow body and headers`() = client.doRequest(
        method = HttpMethod.GET,
        url = "/api/users",
        expectedStatus = 200,
        expectedHeaders = mapOf("x-total-count" to "2"),
        expectedBody = """[{"id":"1","name":"John"},{"id":"2","name":"Jane"}]""",
    )

    @Test
    fun `single response with body`() = client.doRequest(
        method = HttpMethod.POST,
        url = "/api/users",
        requestBody = """{"id":"3","name":"Foo"}""",
        expectedStatus = 201,
        expectedBody = """{"id":"3","name":"Foo"}""",
    )

    @Test
    fun `single response 200 without body`() {
        client.put().uri("/api/users/1")
            .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"id":"1","name":"John"}""")
            .exchange()
            .expectStatus().isEqualTo(200)
            .expectHeader().valueEquals("x-user-id", "1")
            .expectHeader().doesNotExist("Content-Type")
            .expectBody().isEmpty()
    }

    @Test
    fun `single response without body`() = client.doRequest(
        method = HttpMethod.DELETE,
        url = "/api/users/1",
        expectedStatus = 204,
    )

    @Test
    fun `sealed response ok`() = client.doRequest(
        method = HttpMethod.GET,
        url = "/api/users/1",
        expectedStatus = 200,
        expectedHeaders = mapOf("x-user-id" to "1", "ETag" to "\"user-1-v1\""),
        expectedBody = """{"id":"1","name":"John"}""",
    )

    @Test
    fun `sealed response not modified`() = client.doRequest(
        method = HttpMethod.GET,
        url = "/api/users/1",
        requestHeaders = mapOf("If-None-Match" to "\"user-1-v1\""),
        expectedStatus = 304,
        expectedHeaders = mapOf("ETag" to "\"user-1-v1\""),
    )

    @Test
    fun `sealed response modified`() = client.doRequest(
        method = HttpMethod.GET,
        url = "/api/users/1",
        requestHeaders = mapOf("If-None-Match" to "\"user-1-v0\""),
        expectedStatus = 200,
        expectedBody = """{"id":"1","name":"John"}""",
    )

    @Test
    fun `sealed response without body`() = client.doRequest(
        method = HttpMethod.GET,
        url = "/api/users/unauthorized",
        expectedStatus = 401,
        expectedHeaders = mapOf("WWW-Authenticate" to "Basic"),
    )

    @Test
    fun `response by exception thrown in controller`() {
        // exceptions bypass the ApiResponseResultHandler and are rendered by Spring Boot's error handling,
        // whose body contains the dynamic fields `timestamp` and `requestId`
        val body = client.get().uri("/api/users/unauthorized-exception").exchange()
            .expectStatus().isEqualTo(401)
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody().returnResult().responseBody!!.decodeToString()
            .let { Json.parseToJsonElement(it).jsonObject }
        body.keys shouldBe setOf("timestamp", "path", "status", "error", "requestId")
        body["path"]?.jsonPrimitive?.content shouldBe "/api/users/unauthorized-exception"
        body["status"]?.jsonPrimitive?.int shouldBe 401
        body["error"]?.jsonPrimitive?.content shouldBe "Unauthorized"
    }

    @Test
    fun `sealed response with error body`() = client.doRequest(
        method = HttpMethod.GET,
        url = "/api/users/2",
        expectedStatus = 404,
        expectedBody = """{"message":"User 2 not found"}""",
    )

    @Test
    fun `sealed response with status range`() = client.doRequest(
        method = HttpMethod.GET,
        url = "/api/users/error",
        expectedStatus = 503,
        expectedBody = """{"message":"service unavailable"}""",
    )

    @Test
    fun `sealed response default`() = client.doRequest(
        method = HttpMethod.GET,
        url = "/api/users/teapot",
        expectedStatus = 418,
    )

    @Test
    fun `response with non json content type`() {
        client.get().uri("/api/users/1/avatar").exchange()
            .expectStatus().isEqualTo(200)
            .expectHeader().contentType(MediaType.IMAGE_PNG)
            .expectBody().returnResult().responseBody shouldBe byteArrayOf(1, 2, 3)
    }

    @Test
    fun `error response with other content type than success response`() = client.doRequest(
        method = HttpMethod.GET,
        url = "/api/users/2/avatar",
        expectedStatus = 404,
        expectedBody = """{"message":"User 2 not found"}""",
    )

    private fun WebTestClient.doRequest(
        method: HttpMethod,
        url: String,
        requestBody: String? = null,
        requestHeaders: Map<String, String> = emptyMap(),
        expectedStatus: Int,
        expectedHeaders: Map<String, String> = emptyMap(),
        expectedBody: String? = null,
    ) {
        val result = method(method).uri(url)
            .headers { h -> requestHeaders.forEach { (name, value) -> h.add(name, value) } }
            .apply { if (requestBody != null) contentType(MediaType.APPLICATION_JSON).bodyValue(requestBody) }
            .exchange()
            .expectStatus().isEqualTo(expectedStatus)
            .apply {
                expectedHeaders.forEach { (name, value) -> expectHeader().valueEquals(name, value) }
                if (expectedBody != null)
                    expectHeader().contentType(MediaType.APPLICATION_JSON)
            }
            .expectBody().returnResult()
        result.responseBody?.decodeToString()?.takeIf { it.isNotEmpty() } shouldBe expectedBody
    }
}
