package de.quati.ogen.plugin.intern.parsing

import de.quati.ogen.plugin.intern.model.ComponentName
import de.quati.ogen.plugin.intern.model.Endpoint
import de.quati.ogen.plugin.intern.model.RefString
import de.quati.ogen.plugin.intern.parsing.helper.ParserContext
import de.quati.ogen.plugin.intern.parsing.helper.SchemaLocation

context(_: ParserContext)
internal fun io.swagger.v3.oas.models.parameters.Parameter.parse(
    name: ComponentName.Parameter?,
): Endpoint.Parameter {
    if (`$ref` != null)
        return Endpoint.Parameter.Ref(RefString.Parameter.parse(`$ref`!!))
    if (name == null)
        error("Parameter name and ref is null")
    val paramName = this@parse.name!!
    val schema = schema.parse(
        location = SchemaLocation.Root(
            type = SchemaLocation.Root.Type.COMPONENTS_PARAMETER,
            name = name.schemaName,
        )
    )
    val type = Endpoint.Parameter.Type.entries.firstOrNull { it.name.equals(`in`, ignoreCase = true) }
        ?: error("Parameter in-type '$paramName' is invalid")
    return Endpoint.Parameter.Content(
        key = name,
        name = paramName,
        type = type,
        description = description,
        required = (type == Endpoint.Parameter.Type.PATH) || (required ?: false),
        schema = schema,
    )
}
