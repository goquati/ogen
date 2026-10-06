package de.quati.ogen.plugin.intern.parsing

import de.quati.ogen.plugin.intern.model.Component
import de.quati.ogen.plugin.intern.model.ComponentName
import de.quati.ogen.plugin.intern.model.ContentType
import de.quati.ogen.plugin.intern.model.Endpoint
import de.quati.ogen.plugin.intern.model.RefString
import de.quati.ogen.plugin.intern.parsing.helper.ParserContext

context(c: ParserContext)
internal fun io.swagger.v3.oas.models.parameters.RequestBody?.parse(
    name: ComponentName.RequestBody
): Endpoint.RequestBody {
    if (this == null)
        return Endpoint.RequestBody.Empty
    if (`$ref` != null)
        return Endpoint.RequestBody.Ref(RefString.RequestBody.parse(`$ref`!!))
    val content = content ?: return Endpoint.RequestBody.Empty
    return Endpoint.RequestBody.Content(
        key = name,
        required = required ?: false,
        description = description,
        content = content.map { (key, value) ->
            val contentType = ContentType.parse(key!!)
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