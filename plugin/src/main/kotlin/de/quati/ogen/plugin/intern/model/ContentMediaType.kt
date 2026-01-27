package de.quati.ogen.plugin.intern.model

internal data class ContentMediaType(
    val contentType: ContentType,
    val schema: Component.Schema?,
)

internal fun Collection<ContentMediaType>.combine(): ContentMediaType? {
    if (isEmpty()) return null
    val contentType = map { it.contentType }.distinct().reduce { acc, contentType -> acc + contentType }
    val schema = map { it.schema }.distinct().singleOrNull()
    return ContentMediaType(
        contentType = contentType,
        schema = schema,
    )
}
