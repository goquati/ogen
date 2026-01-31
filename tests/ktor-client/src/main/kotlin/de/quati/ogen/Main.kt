package de.quati.ogen

import de.quati.ogen.gen.client.UsersApi.Companion.usersApi
import de.quati.ogen.gen.client.util.HttpClientOgen
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.statement.discardRemaining


suspend fun main() {
    val client = HttpClientOgen(
        httpClient = HttpClient(CIO) {

        },
        baseUrl = "http://localhost:8080",
    )
    val response = client.usersApi.getUsers()

    response.raw.discardRemaining() // TODO ??
}
