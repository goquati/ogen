package de.quati.ogen.plugin.intern.parsing

import de.quati.ogen.plugin.intern.model.ComponentName
import de.quati.ogen.plugin.intern.model.ContentType
import de.quati.ogen.plugin.intern.model.Endpoint
import de.quati.ogen.plugin.intern.model.RefString
import de.quati.ogen.plugin.intern.model.SpecInfoContext


context(_: SpecInfoContext)
internal fun io.swagger.v3.oas.models.responses.ApiResponse.parse(
    name: ComponentName.Response,
): Endpoint.Response {
    if (`$ref` != null)
        return Endpoint.Response.Ref(RefString.Response.parse(`$ref`!!))
    val content = content ?: return Endpoint.Response.Empty
    return Endpoint.Response.Content(
        key = name,
        description = description,
        content = content.map { (key, value) ->
            value.parse(
                name = name.schemaName,
                contentType = ContentType.parse(key!!),
            )
        }
    )
}
