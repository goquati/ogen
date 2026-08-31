package de.quati.ogen.plugin.intern.parsing

import de.quati.ogen.plugin.intern.model.ComponentName
import de.quati.ogen.plugin.intern.model.ContentMediaType
import de.quati.ogen.plugin.intern.model.ContentType
import de.quati.ogen.plugin.intern.model.Discriminator
import de.quati.ogen.plugin.intern.model.RefString
import de.quati.ogen.plugin.intern.model.Tag
import de.quati.ogen.plugin.intern.parsing.helper.ParserContext
import de.quati.ogen.plugin.intern.parsing.helper.SchemaLocation
import io.swagger.v3.oas.models.Operation

internal fun Operation.parseTag()  = Tag.parse(tags?.firstOrNull() ?: "base")

context(_: ParserContext)
internal fun io.swagger.v3.oas.models.media.MediaType.parse(
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

internal fun io.swagger.v3.oas.models.media.Discriminator.parse(): Discriminator? {
    return Discriminator(
        propertyName = propertyName ?: return null,
        mapping = mapping?.mapValues { RefString.Schema.parse(it.value ?: return null) },
    )
}
