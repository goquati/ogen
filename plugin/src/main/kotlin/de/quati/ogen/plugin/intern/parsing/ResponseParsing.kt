package de.quati.ogen.plugin.intern.parsing

import de.quati.ogen.plugin.intern.model.ComponentName
import de.quati.ogen.plugin.intern.model.ContentType
import de.quati.ogen.plugin.intern.model.Endpoint
import de.quati.ogen.plugin.intern.model.RefString
import de.quati.ogen.plugin.intern.oas.model.RefOr
import de.quati.ogen.plugin.intern.oas.model.Response
import de.quati.ogen.plugin.intern.parsing.helper.ParserContext

context(_: ParserContext)
internal fun RefOr<Response>.parse(
    name: ComponentName.Response,
): Endpoint.Response {
    val response = when (this) {
        is RefOr.Ref -> return Endpoint.Response.Ref(RefString.Response.parse(ref))
        is RefOr.Value -> value
    }
    val content = response.content ?: return Endpoint.Response.Empty
    return Endpoint.Response.Content(
        key = name,
        description = response.description,
        content = content.map { (key, value) ->
            value.parse(
                name = name.schemaName,
                contentType = ContentType.parse(key),
            )
        }
    )
}
