package de.quati.ogen

import de.quati.ogen.oas.schemas.gen.model.TenantDto
import de.quati.ogen.oas.schemas.gen.model.TenantIdDto
import de.quati.ogen.oas.schemas.gen.model.UserDto
import de.quati.ogen.oas.schemas.gen.model.UserIdDto
import de.quati.ogen.oas.schemas.gen.server.TenantsApi
import de.quati.ogen.oas.schemas.gen.server.UsersApi
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import kotlin.uuid.Uuid


@SpringBootApplication
class BaseApplication

fun main(args: Array<String>) {
    runApplication<BaseApplication>(*args)
}

@Service
class UsersController : UsersApi {
    override suspend fun getUsers(): ResponseEntity<UserDto> = UserDto(
        id = UserIdDto(Uuid.parse("75897dbc-8dea-4d14-82c6-dd0ee2243cb3")),
        name = "John Doe"
    ).let { ResponseEntity.ok(it) }
}

@Service
class TenantsController : TenantsApi {
    override suspend fun getTenants(): ResponseEntity<TenantDto> = TenantDto(
        id = TenantIdDto(Uuid.parse("75897dbc-8dea-4d14-82c6-dd0ee2243cb3")),
        name = "John Doe"
    ).let { ResponseEntity.ok(it) }
}