package de.quati.ogen.plugin.intern.parsing

import de.quati.ogen.plugin.intern.model.ComponentName
import de.quati.ogen.plugin.intern.model.Endpoint
import de.quati.ogen.plugin.intern.model.RefString
import de.quati.ogen.plugin.intern.oas.model.Parameter
import de.quati.ogen.plugin.intern.oas.model.RefOr
import de.quati.ogen.plugin.intern.parsing.helper.ParserContext
import de.quati.ogen.plugin.intern.parsing.helper.SchemaLocation

context(_: ParserContext)
internal fun RefOr<Parameter>.parse(
    name: ComponentName.Parameter?,
): Endpoint.Parameter = when (this) {
    is RefOr.Ref -> Endpoint.Parameter.Ref(RefString.Parameter.parse(ref))
    is RefOr.Value -> value.parse(name ?: error("Parameter name and ref is null"))
}

context(_: ParserContext)
private fun Parameter.parse(name: ComponentName.Parameter): Endpoint.Parameter {
    val paramName = this.name
    val schema = (schema ?: error("Parameter '$paramName' has no schema")).parse(
        location = SchemaLocation.Root(
            type = SchemaLocation.Root.Type.COMPONENTS_PARAMETER,
            name = name.schemaName,
        )
    )
    val type = Endpoint.Parameter.Type.entries.firstOrNull { it.name == location.name }
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
