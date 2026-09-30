package de.quati.ogen

import de.quati.ogen.gen.model.ErrorDto
import de.quati.ogen.gen.model.UserDto
import de.quati.ogen.gen.server.UsersApi
import de.quati.ogen.gen.server.UsersApi.GetUserResponse
import kotlinx.coroutines.flow.flowOf
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class UsersController : UsersApi {
    override suspend fun getUsers() = UsersApi.GetUsersResponse(
        body = flowOf(UserDto(id = "1", name = "John"), UserDto(id = "2", name = "Jane")),
        headers = HttpHeaders().apply { add("x-total-count", "2") },
    )

    override suspend fun createUser(userDto: UserDto) =
        UsersApi.CreateUserResponse(body = userDto)

    override suspend fun getUser(userId: String): GetUserResponse = when (userId) {
        "1" -> GetUserResponse.Ok(
            body = UserDto(id = userId, name = "John"),
            headers = HttpHeaders().apply {
                add("x-user-id", userId)
                eTag = "\"user-$userId-v1\""
            },
        )

        "unauthorized" -> GetUserResponse.Unauthorized(
            headers = HttpHeaders().apply { add(HttpHeaders.WWW_AUTHENTICATE, "Basic") },
        )

        "unauthorized-exception" -> throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Hello World")

        "error" -> GetUserResponse.ServerError(
            status = HttpStatus.SERVICE_UNAVAILABLE,
            body = ErrorDto(message = "service unavailable"),
        )

        "teapot" -> GetUserResponse.Default(status = HttpStatusCode.valueOf(418))
        else -> GetUserResponse.NotFound(body = ErrorDto(message = "User $userId not found"))
    }

    override suspend fun getUserAvatar(userId: String) = when (userId) {
        "1" -> UsersApi.GetUserAvatarResponse.Ok(body = byteArrayOf(1, 2, 3))
        else -> UsersApi.GetUserAvatarResponse.NotFound(body = ErrorDto(message = "User $userId not found"))
    }

    override suspend fun updateUser(userId: String, userDto: UserDto) =
        UsersApi.UpdateUserResponse(headers = HttpHeaders().apply { add("x-user-id", userId) })

    override suspend fun deleteUser(userId: String) = UsersApi.DeleteUserResponse()
}
