package de.quati.ogen.plugin.intern.model

internal data class ContentMediaType(
    val contentType: ContentType,
    val schema: Component.Schema?,
)

internal fun Collection<ContentMediaType>.combine(): ContentMediaType? {
    if (isEmpty()) return null
    val contentType = map { it.contentType }.distinct().reduce { acc, contentType -> acc + contentType }
    // validation keywords don't change the type, so schemas differing only in them are the same
    val schema = map { it.schema }.distinctBy { it?.withoutConstraints() }.singleOrNull()
    return ContentMediaType(
        contentType = contentType,
        schema = schema,
    )
}
