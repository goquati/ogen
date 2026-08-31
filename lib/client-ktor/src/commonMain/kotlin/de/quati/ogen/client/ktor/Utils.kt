package de.quati.ogen.client.ktor

import de.quati.ogen.core.SecurityRequirement
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.util.AttributeKey

public val ogenAuthAttr: AttributeKey<List<SecurityRequirement>> = AttributeKey("ogenAuth")

public fun HttpRequestBuilder.getOgenAuthNotes(): List<SecurityRequirement> =
    attributes.getOrNull(ogenAuthAttr) ?: emptyList()
