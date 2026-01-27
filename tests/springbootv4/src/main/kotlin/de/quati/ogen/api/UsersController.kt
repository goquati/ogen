package de.quati.ogen.api

import de.quati.ogen.AuthContext
import de.quati.ogen.UserId
import de.quati.ogen.gen.model.LocaleDto
import de.quati.ogen.gen.model.TenantIdDto
import de.quati.ogen.gen.model.UserCreateDto
import de.quati.ogen.gen.model.UserDto
import de.quati.ogen.gen.model.UserUpdateDto
import de.quati.ogen.gen.server.UsersApi
import kotlinx.coroutines.flow.flowOf
import org.springframework.stereotype.Service
import kotlin.uuid.Uuid

@Service
class UsersController : UsersApi {
    override suspend fun getUsers(
        ctx: AuthContext,
        op: UsersApi.GetUsersContext,
        tenantId: TenantIdDto?,
        isEmailVerified: Boolean?,
        search: String?,
        email: String?
    ) = flowOf(
        UserDto(
            id = UserId(Uuid.parse("75897dbc-8dea-4d14-82c6-dd0ee2243cb3")),
            firstName = "John",
            lastName = "Doe",
            email = "foo@bar.de",
            isEmailVerified = false,
            locale = LocaleDto.EN,
            tenants = emptyList()
        ),
        UserDto(
            id = UserId(Uuid.parse("c449c5c7-3fd1-4a5b-85b3-75a5b957b9cb")),
            firstName = "Jane",
            lastName = "Deo",
            email = "hello@world.de",
            isEmailVerified = true,
            locale = LocaleDto.DE,
            tenants = listOfNotNull(
                TenantIdDto(Uuid.parse("9df36116-ca51-45eb-9e2e-713d348f855a")),
                TenantIdDto(Uuid.parse("75897dbc-8dea-4d14-82c6-dd0ee2243cb3")),
            ),
        ),
    ).let {
        op.createResponse(it) {
            addInputHeader(ctx.name, tenantId, isEmailVerified, search, email)
        }
    }

    override suspend fun createUser(
        ctx: AuthContext,
        op: UsersApi.CreateUserContext,
        userCreateDto: UserCreateDto
    ) = UserDto(
        id = UserId(Uuid.parse("75897dbc-8dea-4d14-82c6-dd0ee2243cb3")),
        firstName = "Foo",
        lastName = "Bar",
        email = "foo@bar.com",
        isEmailVerified = false,
        locale = LocaleDto.EN,
        tenants = listOfNotNull(
            TenantIdDto(Uuid.parse("75897dbc-8dea-4d14-82c6-dd0ee2243cb3")),
            TenantIdDto(Uuid.parse("9df36116-ca51-45eb-9e2e-713d348f855a")),
        )
    ).let {
        op.createResponse(it) {
            addInputHeader(
                ctx.name,
                userCreateDto.firstName,
                userCreateDto.lastName,
                userCreateDto.email,
                userCreateDto.isEmailVerified,
                userCreateDto.locale,
                userCreateDto.tenants,
            )
        }
    }

    override suspend fun getUser(
        ctx: AuthContext,
        op: UsersApi.GetUserContext,
        userId: UserId
    ) = UserDto(
        id = userId,
        firstName = "John",
        lastName = "Doe",
        email = "john.doe@example.com",
        isEmailVerified = true,
        locale = LocaleDto.EN,
        tenants = listOf(TenantIdDto(Uuid.parse("75897dbc-8dea-4d14-82c6-dd0ee2243cb3"))),
    ).let {
        op.createResponse(it) {
            addInputHeader(ctx.name, userId)
        }
    }

    override suspend fun updateUser(
        ctx: AuthContext,
        op: UsersApi.UpdateUserContext,
        userId: UserId,
        userUpdateDto: UserUpdateDto
    ) = op.createResponse {
        addInputHeader(
            ctx.name,
            userId,
            userUpdateDto.locale,
            userUpdateDto.firstName,
            userUpdateDto.lastName,
        )
    }

    override suspend fun deleteUser(
        ctx: AuthContext,
        op: UsersApi.DeleteUserContext,
        userId: UserId
    ) = op.createResponse {
        addInputHeader(ctx.name, userId)
    }

    override suspend fun getUserFile(
        ctx: AuthContext,
        op: UsersApi.GetUserFileContext,
        userId: UserId,
        fileId: String,
        xRequestID: Uuid?
    ) = op.createResponse("file content".toByteArray()) {
        addInputHeader(ctx.name, userId, fileId, xRequestID)
    }

    override suspend fun setPasswordChangeAction(
        ctx: AuthContext,
        op: UsersApi.SetPasswordChangeActionContext,
        userId: UserId
    ) = op.createResponse {
        addInputHeader(ctx.name, userId)
    }

    override suspend fun createEmailChangeRequest(
        ctx: AuthContext,
        op: UsersApi.CreateEmailChangeRequestContext,
        userId: UserId,
        email: String,
        locale: LocaleDto?
    ) = op.createResponse {
        addInputHeader(ctx.name, userId, email, locale)
    }
}