package de.quati.ogen.plugin.intern.parsing

import de.quati.ogen.plugin.intern.model.ComponentName
import de.quati.ogen.plugin.intern.model.ContentMediaType
import de.quati.ogen.plugin.intern.model.ContentType
import de.quati.ogen.plugin.intern.model.Discriminator
import de.quati.ogen.plugin.intern.model.RefString
import de.quati.ogen.plugin.intern.model.SpecInfoContext

context(_: SpecInfoContext)
internal fun io.swagger.v3.oas.models.media.MediaType.parse(
    contentType: ContentType,
    name: ComponentName.Schema,
): ContentMediaType {
    val schema = schema?.parse(name = name)
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
