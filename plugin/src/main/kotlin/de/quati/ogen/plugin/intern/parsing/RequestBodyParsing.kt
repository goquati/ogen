package de.quati.ogen.plugin.intern.parsing

import de.quati.ogen.plugin.intern.model.Component
import de.quati.ogen.plugin.intern.model.ComponentName
import de.quati.ogen.plugin.intern.model.ContentType
import de.quati.ogen.plugin.intern.model.Endpoint
import de.quati.ogen.plugin.intern.model.RefString
import de.quati.ogen.plugin.intern.oas.model.RefOr
import de.quati.ogen.plugin.intern.oas.model.RequestBody
import de.quati.ogen.plugin.intern.parsing.helper.ParserContext

context(c: ParserContext)
internal fun RefOr<RequestBody>?.parse(
    name: ComponentName.RequestBody
): Endpoint.RequestBody {
    val body = when (this) {
        null -> return Endpoint.RequestBody.Empty
        is RefOr.Ref -> return Endpoint.RequestBody.Ref(RefString.RequestBody.parse(ref))
        is RefOr.Value -> value
    }
    return Endpoint.RequestBody.Content(
        key = name,
        required = body.required ?: false,
        description = body.description,
        content = (body.content ?: return Endpoint.RequestBody.Empty).map { (key, value) ->
            val contentType = ContentType.parse(key)
            val registeredBefore = c.getSchemas().keys
            value.parse(
                contentType = contentType,
                name = name.schemaName,
            ).also { media ->
                val schema = media.schema
                if (contentType is ContentType.Multipart && schema is Component.Schema.Ref && schema.name !in registeredBefore)
                    c.markMultipartBodySchema(schema.name)
            }
        }
    )
}
