import de.quati.ogen.BaseApplication
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.Test

@SpringBootTest(
    classes = [BaseApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class OasDirTest {
    @LocalServerPort
    private var port: Int = 0
    private val client by lazy { WebTestClient.bindToServer().baseUrl("http://localhost:$port").build() }

    @Test
    fun `test endpoints`() {
        client.doRequest(
            url = "/api/public/users",
            withAuth = false,
            expectedStatus = 200,
            expectedBodyData = """{"id":"75897dbc-8dea-4d14-82c6-dd0ee2243cb3","name":"user name"}""",
        )
        client.doRequest(
            url = "/api/public/tenants",
            withAuth = false,
            expectedStatus = 200,
            expectedBodyData = """{"id":"75897dbc-8dea-4d14-82c6-dd0ee2243cb3","name":"tenant name"}""",
        )

        client.doRequest(
            url = "/api/orders",
            withAuth = false,
            expectedStatus = 401,
        )
        client.doRequest(
            url = "/api/orders",
            withAuth = true,
            expectedStatus = 200,
            expectedBodyData = """{"id":"75897dbc-8dea-4d14-82c6-dd0ee2243cb3","name":"order name"}""",
        )
        client.doRequest(
            url = "/api/products",
            withAuth = false,
            expectedStatus = 401,
        )
        client.doRequest(
            url = "/api/products",
            withAuth = true,
            expectedStatus = 200,
            expectedBodyData = """{"id":"75897dbc-8dea-4d14-82c6-dd0ee2243cb3","name":"product name"}""",
        )
    }


    private fun WebTestClient.doRequest(
        url: String,
        withAuth: Boolean,
        expectedStatus: Int,
        expectedBodyData: String? = null,
    ): Unit = get().uri { builder ->
        builder.path(url)
        builder.build()
    }
        .apply {
            if (withAuth) headers { it.setBasicAuth("kevin", "123") }
        }
        .exchange()
        .expectStatus().isEqualTo(expectedStatus)
        .apply {
            if (expectedBodyData != null)
                expectHeader().valueEquals("content-type", "application/json")
        }
        .returnResult().responseBodyContent?.let { if (it.isEmpty()) null else it.decodeToString() }.let { bodyResult ->
            if (expectedBodyData != null)
                bodyResult shouldBe expectedBodyData
        }
}