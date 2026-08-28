package de.quati.ogen.plugin.intern.parsing

import de.quati.ogen.plugin.intern.model.ComponentName
import de.quati.ogen.plugin.intern.model.Security
import de.quati.ogen.plugin.intern.model.Spec
import de.quati.ogen.plugin.intern.model.SpecInfoContext
import de.quati.ogen.plugin.intern.model.SpecSecurityContext
import io.swagger.v3.parser.core.models.SwaggerParseResult

internal fun SwaggerParseResult.parse(): Spec {
    val raw = openAPI!!
    val version = if (isOpenapi31)
        Spec.Version.V3_1
    else
        Spec.Version.V3_0

    val securityRequirementObjects = raw.components.securitySchemes?.parse() ?: emptyMap()
    val defaultSecurity = object : SpecSecurityContext {
        override val securityRequirementObjects = securityRequirementObjects
    }.run { raw.security.parse() }

    val infoContext = object : SpecInfoContext {
        override val version: Spec.Version = version
        override val defaultSecurity: Security = defaultSecurity
        override val securityRequirementObjects = securityRequirementObjects
    }
    return with(infoContext) {
        val components = raw.components.parse()
        val paths = raw.paths.parse()
        Spec(
            version = version,
            paths = paths,
            components = components,
            security = defaultSecurity,
        )
    }
}

context(_: SpecInfoContext)
private fun io.swagger.v3.oas.models.Components.parse() = Spec.Components(
    schemas = schemas?.entries?.associate { (name, obj) ->
        val name = ComponentName.Schema.parse(name)
        name to obj.parse(name = name)
    } ?: emptyMap(),
    parameters = parameters?.entries?.associate { (name, obj) ->
        val name = ComponentName.Parameter.parse(name)
        name to obj.parse(name = name)
    } ?: emptyMap(),
    response = responses?.entries?.associate { (name, obj) ->
        val name = ComponentName.Response.parse(name)
        name to obj.parse(name = name)
    } ?: emptyMap(),
    requestBody = requestBodies?.entries?.associate { (name, obj) ->
        val name = ComponentName.RequestBody.parse(name)
        name to obj.parse(name = name)
    } ?: emptyMap(),
)
