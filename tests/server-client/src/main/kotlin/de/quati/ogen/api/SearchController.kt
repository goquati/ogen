package de.quati.ogen.api

import de.quati.kotlin.util.Option
import de.quati.ogen.gen.search.model.SearchRequestDto
import de.quati.ogen.gen.search.model.SearchResultDto
import de.quati.ogen.gen.search.server.SearchApi
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service

@Service
class SearchController : SearchApi {
    override suspend fun searchUsers(searchRequestDto: SearchRequestDto) = ResponseEntity.ok(
        SearchResultDto(
            names = listOf("John Doe", "Jane Deo")
                .filter { it.contains(searchRequestDto.term, ignoreCase = true) }
                .take((searchRequestDto.limit as? Option.Some)?.value ?: Int.MAX_VALUE)
        )
    )
}
