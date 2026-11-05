package de.quati.ogen.plugin.intern.tasks

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.responses.ApiResponse
import org.gradle.api.GradleException
import org.slf4j.Logger

private const val COMPONENT_SCHEMA_REF_PREFIX = "#/components/schemas/"

internal fun checkRequiredProperties(spec: OpenAPI, logger: Logger) {
    val errors = getUndefinedRequiredPropertyErrors(spec)
    if (errors.isEmpty()) return
    errors.forEach { logger.error("\t$it") }
    throw GradleException("Found ${errors.size} required properties without a matching property.")
}

internal fun getUndefinedRequiredPropertyErrors(spec: OpenAPI): List<String> {
    val componentSchemas = spec.components?.schemas.orEmpty()

    fun String.componentName() =
        takeIf { it.startsWith(COMPONENT_SCHEMA_REF_PREFIX) }?.removePrefix(COMPONENT_SCHEMA_REF_PREFIX)

    fun Schema<*>?.allowsAnyName() = this != null && booleanSchemaValue != false

    // Names this schema defines, its reference and allOf/oneOf/anyOf members included; null if it allows any name.
    fun Schema<*>.definedNames(seenRefs: Set<String> = emptySet()): Set<String>? {
        if (additionalProperties == true || (additionalProperties as? Schema<*>).allowsAnyName() ||
            unevaluatedProperties.allowsAnyName() || !patternProperties.isNullOrEmpty()
        ) return null
        val names = properties.orEmpty().keys.toMutableSet()
        `$ref`?.takeIf { it !in seenRefs }?.let { ref ->
            val target = ref.componentName()?.let { componentSchemas[it] } ?: return null
            names += target.definedNames(seenRefs + ref) ?: return null
        }
        for (member in allOf.orEmpty() + oneOf.orEmpty() + anyOf.orEmpty())
            names += member.definedNames(seenRefs) ?: return null
        return names
    }

    // Visits every schema written in the spec with the names it may require: its own, and those its composition
    // parents and their members define (null if any). References are not followed; their targets are visited as components.
    fun forEachSchema(
        componentInherited: (String) -> Set<String>?,
        visit: (schema: Schema<*>, location: String, allowed: Set<String>?) -> Unit,
    ) {
        fun Schema<*>.walk(location: String, inherited: Set<String>? = emptySet()) {
            val allowed = inherited?.let { names -> definedNames()?.let { names + it } }
            visit(this, location, allowed)
            properties?.forEach { (name, prop) -> prop.walk(location = "$location.$name") }
            items?.walk(location = "$location[]")
            (additionalProperties as? Schema<*>)?.walk(location = "$location.additionalProperties")
            allOf?.forEachIndexed { i, member -> member.walk(location = "$location.allOf[$i]", inherited = allowed) }
            oneOf?.forEachIndexed { i, member -> member.walk(location = "$location.oneOf[$i]", inherited = allowed) }
            anyOf?.forEachIndexed { i, member -> member.walk(location = "$location.anyOf[$i]", inherited = allowed) }
        }

        fun Content.walk(location: String) = forEach { (mediaType, media) -> media.schema?.walk("$location:$mediaType") }

        fun Parameter.walk(location: String) {
            schema?.walk(location)
            content?.walk(location)
        }

        fun ApiResponse.walk(location: String) {
            content?.walk(location)
            headers?.forEach { (name, header) ->
                header.schema?.walk("$location:header:$name")
                header.content?.walk("$location:header:$name")
            }
        }

        componentSchemas.forEach { (name, schema) -> schema.walk(location = name, inherited = componentInherited(name)) }
        spec.components?.parameters?.forEach { (name, parameter) -> parameter.walk("parameter:$name") }
        spec.components?.requestBodies?.forEach { (name, body) -> body.content?.walk("requestBody:$name") }
        spec.components?.responses?.forEach { (name, response) -> response.walk("response:$name") }

        // The parser copies path-level parameters into each operation.
        spec.paths.orEmpty().forEach { (path, pathItem) ->
            pathItem.readOperationsMap().forEach { (method, operation) ->
                val location = "$path:${method.name}"
                operation.parameters?.forEach { it.walk("$location.${it.name}") }
                operation.requestBody?.content?.walk("$location:requestBody")
                operation.responses?.forEach { (status, response) -> response.walk("$location:response:$status") }
            }
        }
    }

    // A component referenced as a composition member may require what the referencing schema defines, as an inline member may.
    val referencedMemberNames = mutableMapOf<String, Set<String>?>()
    forEachSchema(componentInherited = { emptySet() }) { schema, _, allowed ->
        (schema.allOf.orEmpty() + schema.oneOf.orEmpty() + schema.anyOf.orEmpty())
            .mapNotNull { it.`$ref`?.componentName() }
            .forEach { name ->
                referencedMemberNames[name] =
                    if (name in referencedMemberNames) referencedMemberNames[name]?.let { names -> allowed?.let { names + it } }
                    else allowed
            }
    }

    val errors = mutableListOf<String>()
    forEachSchema(
        componentInherited = { name -> if (name in referencedMemberNames) referencedMemberNames[name] else emptySet() },
    ) { schema, location, allowed ->
        if (allowed != null)
            schema.required.orEmpty().filter { it !in allowed }
                .forEach { errors.add("required property '$it' in $location is not defined in its properties") }
    }
    return errors
}
