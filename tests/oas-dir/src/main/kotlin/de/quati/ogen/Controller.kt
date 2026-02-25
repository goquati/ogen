package de.quati.ogen

import de.quati.ogen.oas.schemas.gen1.model.TenantDto
import de.quati.ogen.oas.schemas.gen1.model.TenantIdDto
import de.quati.ogen.oas.schemas.gen1.model.UserDto
import de.quati.ogen.oas.schemas.gen1.model.UserIdDto
import de.quati.ogen.oas.schemas.gen1.server.TenantsApi
import de.quati.ogen.oas.schemas.gen1.server.UsersApi
import de.quati.ogen.oas.schemas.gen2.model.OrderDto
import de.quati.ogen.oas.schemas.gen2.model.OrderIdDto
import de.quati.ogen.oas.schemas.gen2.model.ProductDto
import de.quati.ogen.oas.schemas.gen2.model.ProductIdDto
import de.quati.ogen.oas.schemas.gen2.server.OrdersApi
import de.quati.ogen.oas.schemas.gen2.server.ProductsApi
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import kotlin.uuid.Uuid


@Service
class Controller : UsersApi, TenantsApi, ProductsApi, OrdersApi {
    override suspend fun getUsers(): ResponseEntity<UserDto> = UserDto(
        id = UserIdDto(Uuid.parse("75897dbc-8dea-4d14-82c6-dd0ee2243cb3")),
        name = "user name"
    ).let { ResponseEntity.ok(it) }

    override suspend fun getTenants(): ResponseEntity<TenantDto> = TenantDto(
        id = TenantIdDto(Uuid.parse("75897dbc-8dea-4d14-82c6-dd0ee2243cb3")),
        name = "tenant name"
    ).let { ResponseEntity.ok(it) }

    override suspend fun getProducts(ctx: AuthContext): ResponseEntity<ProductDto> = ProductDto(
        id = ProductIdDto(Uuid.parse("75897dbc-8dea-4d14-82c6-dd0ee2243cb3")),
        name = "product name"
    ).let { ResponseEntity.ok(it) }

    override suspend fun getOrders(ctx: AuthContext): ResponseEntity<OrderDto> = OrderDto(
        id = OrderIdDto(Uuid.parse("75897dbc-8dea-4d14-82c6-dd0ee2243cb3")),
        name = "order name"
    ).let { ResponseEntity.ok(it) }
}
