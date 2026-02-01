package de.quati.ogen

import de.quati.ogen.gen.server.DebugApi
import de.quati.ogen.gen.server.UsersApi
import de.quati.ogen.gen.shared.OperationContext
import de.quati.ogen.gen.shared.SecurityRequirement
import de.quati.ogen.gen.shared.SecurityRequirementObject
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpMethod
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.Test

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpringBootServerTest {
    @LocalServerPort
    private var port: Int = 0
    private val client by lazy {
        WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()!!
    }

    @Test
    fun `test getUsers`() {
        val op = Operation(HttpMethod.GET, "/api/v1/users")
        val expectedBody = BodyData(
            status = 200,
            content = """[{"id":"75897dbc-8dea-4d14-82c6-dd0ee2243cb3","firstName":"John","lastName":"Doe","email":"foo@bar.de","isEmailVerified":false,"locale":"en","tenants":[]},{"id":"c449c5c7-3fd1-4a5b-85b3-75a5b957b9cb","firstName":"Jane","lastName":"Deo","email":"hello@world.de","isEmailVerified":true,"locale":"de","tenants":["9df36116-ca51-45eb-9e2e-713d348f855a","75897dbc-8dea-4d14-82c6-dd0ee2243cb3"]}]""",
            type = "application/json",
        )

        client.doRequest(
            op = op, user = User.USER,
            expectedInput = "testUser|null|null|null|null|null",
            expectedBody = expectedBody,
        )

        client.doRequest(
            op = op, query = mapOf("isEmailVerified" to "true"), user = User.USER,
            expectedInput = "testUser|null|true|null|null|null",
            expectedBody = expectedBody,
        )

        client.doRequest(
            op = op, query = mapOf("search" to "foobar"), user = User.USER,
            expectedInput = "testUser|null|null|foobar|null|null",
            expectedBody = expectedBody,
        )

        client.doRequest(
            op = op, query = mapOf("locale" to "de"), user = User.USER,
            expectedInput = "testUser|null|null|null|de|null",
            expectedBody = expectedBody,
        )

        client.doRequest(
            op = op, query = mapOf("email" to "foo@bar.com"), user = User.USER,
            expectedInput = "testUser|null|null|null|null|foo@bar.com",
            expectedBody = expectedBody,
        )

        client.doRequest(
            op = op,
            query = mapOf("tenantId" to "b25f68fb-61c6-4338-b98e-3bdc3afc5c22"),
            user = User.USER,
            expectedInput = "testUser|b25f68fb-61c6-4338-b98e-3bdc3afc5c22|null|null|null|null",
            expectedBody = expectedBody,
        )

        client.doRequest(
            op = op,
            query = mapOf(
                "tenantId" to "b25f68fb-61c6-4338-b98e-3bdc3afc5c22",
                "isEmailVerified" to "false",
                "search" to "hello",
                "locale" to "en",
                "email" to "foo@bar.de"
            ),
            user = User.USER,
            expectedInput = "testUser|b25f68fb-61c6-4338-b98e-3bdc3afc5c22|false|hello|en|foo@bar.de",
            expectedBody = expectedBody,
        )

        client.doRequest(
            op = op, user = User.ADMIN,
            expectedInput = "testAdmin|null|null|null|null|null",
            expectedBody = expectedBody,
        )

        // no user
        client.doRequest(op = op, user = null, expectedStatus = 401)

        // invalid uuid
        client.doRequest(
            op = op,
            user = User.USER,
            query = mapOf("tenantId" to "b25f6"),
            expectedStatus = 400
        )
    }

    @Test
    fun `test createUser`() {
        val op = Operation(HttpMethod.POST, "/api/v1/users")
        val expectedBody = BodyData(
            status = 201,
            content = """{"id":"75897dbc-8dea-4d14-82c6-dd0ee2243cb3","firstName":"Foo","lastName":"Bar","email":"foo@bar.com","isEmailVerified":false,"locale":"en","tenants":["75897dbc-8dea-4d14-82c6-dd0ee2243cb3","9df36116-ca51-45eb-9e2e-713d348f855a"]}""",
            type = "application/json",
        )
        client.doRequest(
            op = op,
            user = User.USER,
            body = """{"email":"foo@bar.de","tenants":[]}""",
            expectedInput = "testUser|Undefined|Undefined|foo@bar.de|Undefined|Undefined|[]",
            expectedBody = expectedBody
        )
        client.doRequest(
            op = op,
            user = User.USER,
            body = """{"firstName":"Foo","lastName":"Bar","email":"foo@bar.com","isEmailVerified":false,"locale":"en","tenants":["75897dbc-8dea-4d14-82c6-dd0ee2243cb3","9df36116-ca51-45eb-9e2e-713d348f855a"]}""",
            expectedInput = "testUser|Some(value=Foo)|Some(value=Bar)|foo@bar.com|Some(value=false)|Some(value=en)|[75897dbc-8dea-4d14-82c6-dd0ee2243cb3, 9df36116-ca51-45eb-9e2e-713d348f855a]",
            expectedBody = expectedBody
        )
        client.doRequest(
            op = op,
            user = User.USER,
            body = """{"email":"foo@bar.de"}""",
            expectedStatus = 400, // tenants is required
        )
        client.doRequest(
            op = op,
            user = User.USER,
            body = """{"tenants":[]}""",
            expectedStatus = 400, // email is required
        )
        client.doRequest(
            op = op,
            user = User.USER,
            body = """{"email":"foo@bar.de","tenants":[],"locale":"invalid"}""",
            expectedStatus = 400, // invalid enum value
        )
    }

    @Test
    fun `test getUser`() {
        val userId = "75897dbc-8dea-4d14-82c6-dd0ee2243cb3"
        val op = Operation(HttpMethod.GET, "/api/v1/users/$userId")
        val expectedBody = BodyData(
            status = 200,
            content = """{"id":"$userId","firstName":"John","lastName":"Doe","email":"john.doe@example.com","isEmailVerified":true,"locale":"en","tenants":["75897dbc-8dea-4d14-82c6-dd0ee2243cb3"]}""",
            type = "application/json",
        )
        client.doRequest(
            op = op,
            user = User.USER,
            expectedInput = "testUser|$userId",
            expectedBody = expectedBody
        )
    }

    @Test
    fun `test updateUser`() {
        val userId = "75897dbc-8dea-4d14-82c6-dd0ee2243cb3"
        val op = Operation(HttpMethod.PUT, "/api/v1/users/$userId")
        client.doRequest(
            op = op,
            user = User.USER,
            body = """{"firstName":"Jane","lastName":"Doe","locale":"de"}""",
            expectedStatus = 200,
            expectedInput = "testUser|$userId|Some(value=de)|Some(value=Jane)|Some(value=Doe)"
        )
    }

    @Test
    fun `test deleteUser`() {
        val userId = "75897dbc-8dea-4d14-82c6-dd0ee2243cb3"
        val op = Operation(HttpMethod.DELETE, "/api/v1/users/$userId")
        client.doRequest(
            op = op,
            user = User.USER,
            expectedStatus = 200,
            expectedInput = "testUser|$userId"
        )
    }

    @Test
    fun `test getUserFile`() {
        val userId = "75897dbc-8dea-4d14-82c6-dd0ee2243cb3"
        val fileId = "file123"
        val op = Operation(HttpMethod.GET, "/api/v1/users/$userId/files/$fileId")
        val expectedBody = BodyData(
            status = 200,
            content = "file content",
            type = "application/octet-stream",
        )
        client.doRequest(
            op = op,
            user = User.USER,
            headers = mapOf("X-Request-ID" to "dfd078df-dd62-425b-ba9e-070a6b130402"),
            expectedInput = "testUser|$userId|$fileId|dfd078df-dd62-425b-ba9e-070a6b130402",
            expectedBody = expectedBody,
        )
        client.doRequest(
            op = op,
            user = User.USER,
            headers = mapOf("X-Request-ID" to "dfd078d"),
            expectedStatus = 400, // X-Request-ID is invalid uuid
        )
    }

    @Test
    fun `test setPasswordChangeAction`() {
        val userId = "75897dbc-8dea-4d14-82c6-dd0ee2243cb3"
        val op = Operation(HttpMethod.POST, "/api/v1/users/$userId:set-password-change-action")
        client.doRequest(
            op = op,
            user = User.USER,
            expectedStatus = 201,
            expectedInput = "testUser|$userId"
        )
    }

    @Test
    fun `test createEmailChangeRequest`() {
        val userId = "75897dbc-8dea-4d14-82c6-dd0ee2243cb3"
        val op = Operation(HttpMethod.POST, "/api/v1/users/$userId:email-change-request")
        client.doRequest(
            op = op,
            user = User.USER,
            query = mapOf("email" to "new@email.com", "locale" to "en"),
            expectedStatus = 201,
            expectedInput = "testUser|$userId|new@email.com|en"
        )
        client.doRequest(
            op = op,
            user = User.USER,
            query = mapOf("locale" to "en"),
            expectedStatus = 400, // email is required
        )
        client.doRequest(
            op = op,
            user = User.USER,
            query = mapOf("email" to "new@email.com", "locale" to "invalid"),
            expectedStatus = 400, // email is invalid enum value
        )
    }

    @Test
    fun `test public debug endpoints`() {
        val op = Operation(HttpMethod.GET, "/api/v1/public/debug")
        client.doRequest(op = op, user = null, expectedStatus = 200, expectedInput = "null")
        client.doRequest(op = op, user = User.USER, expectedStatus = 200, expectedInput = "null")
        client.doRequest(op = op, user = User.ADMIN, expectedStatus = 200, expectedInput = "null")

        val cookies = mapOf("debug-session" to "foo")
        client.doRequest(op = op, user = null, cookies = cookies, expectedStatus = 200, expectedInput = "foo")
        client.doRequest(op = op, user = User.USER, cookies = cookies, expectedStatus = 200, expectedInput = "foo")
        client.doRequest(op = op, user = User.ADMIN, cookies = cookies, expectedStatus = 200, expectedInput = "foo")
    }

    @Test
    fun `test operation context`() {
        UsersApi.GetUsersContext.name shouldBe "getUsers"
        UsersApi.GetUsersContext.description shouldBe null
        UsersApi.GetUsersContext.deprecated shouldBe false
        UsersApi.GetUsersContext.tag shouldBe "Users"
        UsersApi.GetUsersContext.security shouldBe listOf(
            SecurityRequirement(listOf(SecurityRequirementObject.Http(name = "user"))),
            SecurityRequirement(listOf(SecurityRequirementObject.Http(name = "service"))),
        )
        UsersApi.GetUsersContext.defaultSuccessStatus shouldBe 200
        UsersApi.GetUsersContext.requestBody shouldBe null
        UsersApi.GetUsersContext.responses shouldBe mapOf(
            "200" to OperationContext.Body(
                description = "get users",
                contentTypes = setOf("application/json", "application/stream+json"),
            ),
        )

        UsersApi.CreateUserContext.defaultSuccessStatus shouldBe 201
        UsersApi.CreateUserContext.requestBody shouldBe OperationContext.Body(
            description = null,
            contentTypes = setOf("application/json"),
        )

        UsersApi.DeleteUserContext.responses shouldBe emptyMap()

        DebugApi.DebugInfoContext.security shouldBe emptyList()
    }
}