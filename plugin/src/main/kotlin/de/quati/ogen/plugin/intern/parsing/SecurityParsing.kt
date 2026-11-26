package de.quati.ogen.plugin.intern.parsing

import de.quati.ogen.plugin.intern.model.ComponentName
import de.quati.ogen.plugin.intern.model.Security
import de.quati.ogen.plugin.intern.model.SecuritySchemeType
import de.quati.ogen.plugin.intern.model.SpecSecurityContext
import de.quati.ogen.plugin.intern.oas.model.RefOr
import de.quati.ogen.plugin.intern.oas.model.SecurityRequirement
import de.quati.ogen.plugin.intern.oas.model.SecurityScheme

/** The security schemes by name; a reference to another scheme of the components is followed. */
internal fun Map<String, RefOr<SecurityScheme>>.parse(): Map<String, Security.RequirementObject> =
    entries.associate { (name, scheme) ->
        name to Security.RequirementObject(
            name = ComponentName.Security.parse(name),
            type = SecuritySchemeType.valueOf(resolve(name, scheme).type.name),
        )
    }

private fun Map<String, RefOr<SecurityScheme>>.resolve(name: String, scheme: RefOr<SecurityScheme>): SecurityScheme {
    val seen = mutableSetOf(name)
    var current = scheme
    while (true) {
        when (current) {
            is RefOr.Value -> return current.value
            is RefOr.Ref -> {
                val target = current.ref.removePrefix(LOCAL_PREFIX).takeIf { current.ref.startsWith(LOCAL_PREFIX) }
                    ?: error("Security scheme '$name' refers to '${current.ref}', which is no security scheme of the components")
                if (!seen.add(target)) error("Security scheme '$name' refers to itself")
                current = get(target) ?: error("Security scheme '$name' refers to the unknown scheme '$target'")
            }
        }
    }
}

private const val LOCAL_PREFIX = "#/components/securitySchemes/"

context(s: SpecSecurityContext)
internal fun List<SecurityRequirement>?.parse() =
    Security(this?.map { req ->
        req.keys.map {
            s.securityRequirementObjects[it] ?: throw IllegalArgumentException("Unknown security scheme: $it")
        }
    } ?: emptyList())
