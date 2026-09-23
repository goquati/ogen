package de.quati.ogen

import de.quati.ogen.client.ktor.HttpResponseTyped
import io.kotest.matchers.shouldBe
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpMethod
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters


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
    expectedNoBody: Boolean? = null,
) = doRequest(
    op = op, user = user, query = query, cookies = cookies, body = body, headers = headers,
    expectedStatus = expectedStatus,
    expectedInput = expectedInput,
    expectedNoBody = expectedNoBody,
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
    expectedNoBody = null,
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
    expectedNoBody: Boolean?,
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
        if (expectedNoBody == true) expectHeader().valueEquals("content-type") // no type
    }
    .returnResult().responseBodyContent
    .also {
        if (expectedNoBody == true)
            (it?.size ?: 0) shouldBe 0
    }
    ?.let { if (it.isEmpty()) null else it.decodeToString() }.let { bodyResult ->
        if (expectedBodyData != null) bodyResult shouldBe expectedBodyData
    }


fun WebTestClient.doMultipartRequest(
    op: Operation,
    user: User?,
    parts: Map<String, String>,
    fileName: String?,
    fileContent: String?,
    expectedStatus: Int,
    expectedInput: String? = null,
    expectedBodyData: String? = null,
): Unit = method(op.method).uri(op.url)
    .apply {
        if (user != null) headers { it.setBasicAuth(user.userName, user.password) }
        body(
            BodyInserters.fromMultipartData(MultipartBodyBuilder().apply {
                parts.forEach { (name, value) -> part(name, value) }
                if (fileName != null && fileContent != null)
                    part("file", ByteArrayResource(fileContent.toByteArray())).filename(fileName)
            }.build())
        )
    }
    .exchange()
    .expectStatus().isEqualTo(expectedStatus)
    .apply {
        if (expectedInput != null) expectHeader().valueEquals("input-data", expectedInput)
    }
    .returnResult().responseBodyContent
    ?.let { if (it.isEmpty()) null else it.decodeToString() }.let { bodyResult ->
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