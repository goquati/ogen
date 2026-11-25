package de.quati.ogen

import de.quati.ogen.gen.model.CodeDto
import de.quati.ogen.gen.model.ItemDto
import de.quati.ogen.gen.server.ItemsApi
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service

@Service
class ItemsController : ItemsApi {
    override suspend fun getItem(
        itemId: String,
        limit: Int?,
        offset: Long?,
        score: Double?,
        search: String?,
        tags: List<String>?,
        code: CodeDto?,
        xRequestId: String?,
    ): ResponseEntity<Any> = ResponseEntity.ok(
        listOf(itemId, limit, offset, score, search, tags, code, xRequestId).joinToString("|")
    )

    override suspend fun createItem(itemDto: ItemDto) = ResponseEntity.ok(itemDto)

    override suspend fun createItems(listItemDto: List<ItemDto>) = ResponseEntity.ok(listItemDto.size)
}
