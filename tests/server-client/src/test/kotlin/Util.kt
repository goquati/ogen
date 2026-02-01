package de.quati.ogen

import de.quati.ogen.gen.client.util.HttpResponseTyped
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpMethod
import org.springframework.test.web.reactive.server.WebTestClient


data class BodyData<T>(
    val status: Int,
    val content: T,
    val type: String,
)

data class Operation(
    val method: HttpMethod,
    val url: String,
)

enum class User(
    val userName: String, val password: String
) {
    USER("testUser", "123"),
    ADMIN("testAdmin", "456")
}


fun WebTestClient.doRequest(
    op: Operation,
    user: User?,
    query: Map<String, String>? = null,
    cookies: Map<String, String>? = null,
    headers: Map<String, String>? = null,
    body: Any? = null,
    expectedStatus: Int,
    expectedInput: String? = null,
) = doRequest(
    op = op, user = user, query = query, cookies = cookies, body = body, headers = headers,
    expectedStatus = expectedStatus,
    expectedInput = expectedInput,
    expectedBodyData = null,
    expectedBodyType = null,
)

fun WebTestClient.doRequest(
    op: Operation,
    user: User?,
    query: Map<String, String>? = null,
    cookies: Map<String, String>? = null,
    headers: Map<String, String>? = null,
    body: Any? = null,
    expectedInput: String,
    expectedBody: BodyData<String>,
) = doRequest(
    op = op, user = user, query = query, cookies = cookies, body = body, headers = headers,
    expectedStatus = expectedBody.status,
    expectedInput = expectedInput,
    expectedBodyType = expectedBody.type,
    expectedBodyData = expectedBody.content
)

private fun WebTestClient.doRequest(
    op: Operation,
    user: User?,
    query: Map<String, String>?,
    cookies: Map<String, String>?,
    headers: Map<String, String>?,
    body: Any?,
    expectedStatus: Int,
    expectedInput: String?,
    expectedBodyType: String?,
    expectedBodyData: String?,
): Unit = method(op.method).uri { builder ->
    builder.path(op.url)
    query?.forEach { (k, v) -> builder.queryParam(k, v) }
    builder.build()
}
    .apply {
        if (user != null) headers { it.setBasicAuth(user.userName, user.password) }
        headers?.forEach { (k, v) -> header(k, v) }
        cookies?.forEach { (k, v) -> cookie(k, v) }
        if (body != null) {
            header("Content-Type", "application/json")
            bodyValue(body)
        }
    }
    .exchange()
    .expectStatus().isEqualTo(expectedStatus)
    .apply {
        if (expectedInput != null) expectHeader().valueEquals("input-data", expectedInput)
        if (expectedBodyType != null) expectHeader().valueEquals("content-type", expectedBodyType)
    }
    .returnResult().responseBodyContent?.let { if (it.isEmpty()) null else it.decodeToString() }.let { bodyResult ->
        if (expectedBodyData != null) bodyResult shouldBe expectedBodyData
    }


fun <T : Any> HttpResponseTyped<T>.check(
    expectedStatus: Int,
    expectedContentType: String? = null,
    expectedInput: String? = null,
): HttpResponseTyped<T> {
    status.value shouldBe expectedStatus
    if (expectedContentType != null)
        raw.headers["content-type"] shouldBe expectedContentType
    if (expectedInput != null)
        raw.headers["input-data"] shouldBe expectedInput
    return this
}

suspend fun <T : Any> HttpResponseTyped<T>.check(
    expectedBody: BodyData<T>,
    expectedInput: String,
): HttpResponseTyped<T> {
    status.value shouldBe expectedBody.status
    raw.headers["input-data"] shouldBe expectedInput
    raw.headers["content-type"] shouldBe expectedBody.type
    body() shouldBe expectedBody.content
    return this
}