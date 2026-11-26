package de.quati.ogen.plugin.intern.parsing

import de.quati.ogen.plugin.intern.model.ComponentName
import de.quati.ogen.plugin.intern.model.ContentMediaType
import de.quati.ogen.plugin.intern.model.ContentType
import de.quati.ogen.plugin.intern.model.Discriminator
import de.quati.ogen.plugin.intern.model.RefString
import de.quati.ogen.plugin.intern.model.Tag
import de.quati.ogen.plugin.intern.oas.model.MediaType
import de.quati.ogen.plugin.intern.oas.model.Operation
import de.quati.ogen.plugin.intern.oas.model.RefOr
import de.quati.ogen.plugin.intern.parsing.helper.ParserContext
import de.quati.ogen.plugin.intern.parsing.helper.SchemaLocation

internal fun Operation.parseTag() = Tag.parse(tags?.firstOrNull() ?: "base")

context(c: ParserContext)
internal fun RefOr<MediaType>.parse(
    contentType: ContentType,
    name: ComponentName.Schema.Root,
): ContentMediaType = c.resolveMediaType(this).parse(contentType = contentType, name = name)

context(_: ParserContext)
internal fun MediaType.parse(
    contentType: ContentType,
    name: ComponentName.Schema.Root,
): ContentMediaType {
    val schema = schema?.parse(
        location = SchemaLocation.Root(
            name = name,
            type = SchemaLocation.Root.Type.PATH_BODY,
        )
    )
    return ContentMediaType(
        contentType = contentType,
        schema = schema,
    )
}

internal fun de.quati.ogen.plugin.intern.oas.model.Discriminator.parse(): Discriminator = Discriminator(
    propertyName = propertyName,
    mapping = mapping?.mapValues { RefString.Schema.parse(it.value) },
)
