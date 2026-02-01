package de.quati.ogen

import de.quati.kotlin.util.toOption
import de.quati.ogen.gen.client.DebugApi.Companion.debugApi
import de.quati.ogen.gen.client.UsersApi.Companion.usersApi
import de.quati.ogen.gen.client.util.HttpClientOgen
import de.quati.ogen.gen.client.util.getOgenAuthNotes
import de.quati.ogen.gen.model.LocaleDto
import de.quati.ogen.gen.model.TenantIdDto
import de.quati.ogen.gen.model.UserCreateDto
import de.quati.ogen.gen.model.UserDto
import de.quati.ogen.gen.model.UserUpdateDto
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import kotlin.test.Test
import kotlin.uuid.Uuid

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KtorClientTest {

    @LocalServerPort
    private var port: Int = 0

    fun createClient(user: User?) = HttpClientOgen(
        httpClient = HttpClient(CIO) {
            install(Auth) {
                if (user != null)
                    basic {
                        sendWithoutRequest { it.getOgenAuthNotes().isNotEmpty() }
                        credentials {
                            BasicAuthCredentials(username = user.userName, password = user.password)
                        }
                    }
            }
            install(ContentNegotiation) {
                json(Json {
                    encodeDefaults = false      // omit Undefined (defaults)
                    explicitNulls = true        // keep "field": null when Some(null)
                })
            }
        },
        baseUrl = "http://localhost:$port",
    )

    private val clientUser by lazy { createClient(user = User.USER) }
    private val clientAdmin by lazy { createClient(user = User.ADMIN) }
    private val clientPublic by lazy { createClient(user = null) }

    @Test
    fun `test getUsers`(): TestResult = runTest {
        val expectedBody = BodyData(
            status = 200,
            type = "application/json",
            content = listOf(
                UserDto(
                    id = UserId(Uuid.parse("75897dbc-8dea-4d14-82c6-dd0ee2243cb3")),
                    firstName = "John",
                    lastName = "Doe",
                    email = "foo@bar.de",
                    isEmailVerified = false,
                    locale = LocaleDto.EN,
                    tenants = emptyList(),
                ),
                UserDto(
                    id = UserId(Uuid.parse("c449c5c7-3fd1-4a5b-85b3-75a5b957b9cb")),
                    firstName = "Jane",
                    lastName = "Deo",
                    email = "hello@world.de",
                    isEmailVerified = true,
                    locale = LocaleDto.DE,
                    tenants = listOf(
                        TenantIdDto(Uuid.parse("9df36116-ca51-45eb-9e2e-713d348f855a")),
                        TenantIdDto(Uuid.parse("75897dbc-8dea-4d14-82c6-dd0ee2243cb3")),
                    ),
                ),
            ),
        )

        clientUser.usersApi.getUsers().check(
            expectedInput = "testUser|null|null|null|null|null",
            expectedBody = expectedBody,
        )

        clientUser.usersApi.getUsers(isEmailVerified = true).check(
            expectedInput = "testUser|null|true|null|null|null",
            expectedBody = expectedBody,
        )
        clientUser.usersApi.getUsers(search = "foobar").check(
            expectedInput = "testUser|null|null|foobar|null|null",
            expectedBody = expectedBody,
        )

        clientUser.usersApi.getUsers(locale = LocaleDto.DE).check(
            expectedInput = "testUser|null|null|null|de|null",
            expectedBody = expectedBody,
        )

        clientUser.usersApi.getUsers(email = "foo@bar.com").check(
            expectedInput = "testUser|null|null|null|null|foo@bar.com",
            expectedBody = expectedBody,
        )

        clientUser.usersApi.getUsers(tenantId = TenantIdDto(Uuid.parse("b25f68fb-61c6-4338-b98e-3bdc3afc5c22"))).check(
            expectedInput = "testUser|b25f68fb-61c6-4338-b98e-3bdc3afc5c22|null|null|null|null",
            expectedBody = expectedBody,
        )

        clientUser.usersApi.getUsers(
            tenantId = TenantIdDto(Uuid.parse("b25f68fb-61c6-4338-b98e-3bdc3afc5c22")),
            isEmailVerified = false,
            search = "hello",
            locale = LocaleDto.EN,
            email = "foo@bar.de"
        ).check(
            expectedInput = "testUser|b25f68fb-61c6-4338-b98e-3bdc3afc5c22|false|hello|en|foo@bar.de",
            expectedBody = expectedBody,
        )

        clientAdmin.usersApi.getUsers().check(
            expectedInput = "testAdmin|null|null|null|null|null",
            expectedBody = expectedBody,
        )

        // no user
        clientPublic.usersApi.getUsers().check(expectedStatus = 401)
    }

    @Test
    fun `test createUser`(): TestResult = runTest {
        val expectedBody = BodyData(
            status = 201,
            type = "application/json",
            content = UserDto(
                id = UserId(Uuid.parse("75897dbc-8dea-4d14-82c6-dd0ee2243cb3")),
                firstName = "Foo",
                lastName = "Bar",
                email = "foo@bar.com",
                isEmailVerified = false,
                locale = LocaleDto.EN,
                tenants = listOf(
                    TenantIdDto(Uuid.parse("75897dbc-8dea-4d14-82c6-dd0ee2243cb3")),
                    TenantIdDto(Uuid.parse("9df36116-ca51-45eb-9e2e-713d348f855a")),
                )
            )
        )

        clientUser.usersApi.createUser(
            UserCreateDto(
                email = "foo@bar.de",
                tenants = listOf()
            )
        ).check(
            expectedInput = "testUser|Undefined|Undefined|foo@bar.de|Undefined|Undefined|[]",
            expectedBody = expectedBody
        )

        clientUser.usersApi.createUser(
            UserCreateDto(
                firstName = "Foo".toOption(),
                lastName = "Bar".toOption(),
                email = "foo@bar.com",
                isEmailVerified = false.toOption(),
                locale = LocaleDto.EN.toOption(),
                tenants = listOf(
                    TenantIdDto(Uuid.parse("75897dbc-8dea-4d14-82c6-dd0ee2243cb3")),
                    TenantIdDto(Uuid.parse("9df36116-ca51-45eb-9e2e-713d348f855a")),
                )
            )
        ).check(
            expectedInput = "testUser|Some(value=Foo)|Some(value=Bar)|foo@bar.com|Some(value=false)|Some(value=en)|[75897dbc-8dea-4d14-82c6-dd0ee2243cb3, 9df36116-ca51-45eb-9e2e-713d348f855a]",
            expectedBody = expectedBody
        )
    }

    @Test
    fun `test getUser`(): TestResult = runTest {
        clientUser.usersApi.getUser(UserId("75897dbc-8dea-4d14-82c6-dd0ee2243cb3")).check(
            expectedInput = "testUser|75897dbc-8dea-4d14-82c6-dd0ee2243cb3",
            expectedBody = BodyData(
                status = 200,
                type = "application/json",
                content = UserDto(
                    id = UserId("75897dbc-8dea-4d14-82c6-dd0ee2243cb3"),
                    firstName = "John",
                    lastName = "Doe",
                    email = "john.doe@example.com",
                    isEmailVerified = true,
                    locale = LocaleDto.EN,
                    tenants = listOf(
                        TenantIdDto(Uuid.parse("75897dbc-8dea-4d14-82c6-dd0ee2243cb3")),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `test updateUser`(): TestResult = runTest {
        val userId = UserId("75897dbc-8dea-4d14-82c6-dd0ee2243cb3")
        clientUser.usersApi.updateUser(
            userId = userId,
            userUpdateDto = UserUpdateDto(
                firstName = "Jane".toOption(),
                lastName = "Doe".toOption(),
                locale = LocaleDto.DE.toOption(),
            )
        ).check(
            expectedStatus = 200,
            expectedInput = "testUser|$userId|Some(value=de)|Some(value=Jane)|Some(value=Doe)"
        )
    }

    @Test
    fun `test deleteUser`(): TestResult = runTest {
        val userId = UserId("75897dbc-8dea-4d14-82c6-dd0ee2243cb3")
        clientUser.usersApi.deleteUser(userId).check(
            expectedStatus = 200,
            expectedInput = "testUser|$userId"
        )
    }

    @Test
    fun `test getUserFile`(): TestResult = runTest {
        clientUser.usersApi.getUserFile(
            userId = UserId("75897dbc-8dea-4d14-82c6-dd0ee2243cb3"),
            fileId = "file123",
            xRequestID = Uuid.parse("dfd078df-dd62-425b-ba9e-070a6b130402"),
        ).check(
            expectedStatus = 200,
            expectedContentType = "application/octet-stream",
            expectedInput = "testUser|75897dbc-8dea-4d14-82c6-dd0ee2243cb3|file123|dfd078df-dd62-425b-ba9e-070a6b130402",
        ).also { response ->
            response.raw.bodyAsText() shouldBe "file content"
        }
    }

    @Test
    fun `test setPasswordChangeAction`(): TestResult = runTest {
        clientUser.usersApi.setPasswordChangeAction(UserId("75897dbc-8dea-4d14-82c6-dd0ee2243cb3")).check(
            expectedStatus = 201,
            expectedInput = "testUser|75897dbc-8dea-4d14-82c6-dd0ee2243cb3"
        )
    }

    @Test
    fun `test createEmailChangeRequest`(): TestResult = runTest {
        clientUser.usersApi.createEmailChangeRequest(
            userId = UserId("75897dbc-8dea-4d14-82c6-dd0ee2243cb3"),
            email = "new@email.com",
            locale = LocaleDto.EN,
        ).check(
            expectedStatus = 201,
            expectedInput = "testUser|75897dbc-8dea-4d14-82c6-dd0ee2243cb3|new@email.com|en"
        )
    }

    @Test
    fun `test public debug endpoints`(): TestResult = runTest {
        clientPublic.debugApi.debugInfo().check(expectedStatus = 200, expectedInput = "null")
        clientUser.debugApi.debugInfo().check(expectedStatus = 200, expectedInput = "null")
        clientAdmin.debugApi.debugInfo().check(expectedStatus = 200, expectedInput = "null")

        clientPublic.debugApi.debugInfo(debugSession = "foo").check(expectedStatus = 200, expectedInput = "foo")
        clientUser.debugApi.debugInfo(debugSession = "foo").check(expectedStatus = 200, expectedInput = "foo")
        clientAdmin.debugApi.debugInfo(debugSession = "foo").check(expectedStatus = 200, expectedInput = "foo")
    }
}