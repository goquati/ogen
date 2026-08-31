package de.quati.ogen.plugin.intern.parsing

import de.quati.ogen.plugin.intern.model.ComponentName
import de.quati.ogen.plugin.intern.model.ContentType
import de.quati.ogen.plugin.intern.model.Endpoint
import de.quati.ogen.plugin.intern.model.RefString
import de.quati.ogen.plugin.intern.parsing.helper.ParserContext

context(_: ParserContext)
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
            value.parse(
                contentType = ContentType.parse(key!!),
                name = name.schemaName,
            )
        }
    )
}