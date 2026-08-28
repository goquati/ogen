package de.quati.ogen.plugin.intern.parsing

import de.quati.ogen.plugin.intern.model.ComponentName
import de.quati.ogen.plugin.intern.model.Security
import de.quati.ogen.plugin.intern.model.SpecSecurityContext
import io.swagger.v3.oas.models.security.SecurityScheme


internal fun Map<String, SecurityScheme>.parse(): Map<String, Security.RequirementObject> =
    entries.associate { (name, schema) ->
        name to Security.RequirementObject(
            name = ComponentName.Security.parse(name),
            type = schema.type ?: error("Security scheme '$name' has no type"),
        )
    }

context(s: SpecSecurityContext)
internal fun List<io.swagger.v3.oas.models.security.SecurityRequirement>?.parse() =
    Security(this?.map { req ->
        req.keys.map {
            s.securityRequirementObjects[it] ?: throw IllegalArgumentException("Unknown security scheme: $it")
        }
    } ?: emptyList())