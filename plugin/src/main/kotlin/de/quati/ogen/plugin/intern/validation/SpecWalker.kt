package de.quati.ogen.plugin.intern.validation

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.callbacks.Callback
import io.swagger.v3.oas.models.headers.Header
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import java.util.Collections
import java.util.IdentityHashMap

/** How a schema relates to the value its parent schema describes. */
internal enum class SchemaRole {
    /** Describes another value: a property, an item, a map value. */
    NESTED,

    /** Constrains the parent's own value: `allOf`, `oneOf`, `anyOf`, `if`, `then`, `else`, `dependentSchemas`. */
    APPLIED,

    /** Describes what the parent's own value must not be: `not`. */
    NEGATED,
}

/**
 * A schema written in the spec. [location] names it the same way in every check: `Pet.owner.age`, `Pet.tags[]`,
 * `Pet.allOf[1]`, `/pets:GET.parameters.limit`, `/pets:POST.requestBody.application/json`.
 */
internal class SchemaNode(
    val schema: Schema<*>,
    val location: String,
    val parent: SchemaNode? = null,
    val role: SchemaRole = SchemaRole.NESTED,
    /** Set on the root of a component schema. */
    val componentName: String? = null,
) {
    val isInsideNot: Boolean get() = role == SchemaRole.NEGATED || parent?.isInsideNot == true
}

/** An operation; [pathItemLocation] is its path, or where its path item sits (`webhooks.newPet`, a callback). */
internal class OperationNode(val pathItemLocation: String, val method: PathItem.HttpMethod, val operation: Operation) {
    val location get() = "$pathItemLocation:${method.name}"
}

internal val Schema<*>.compositionMembers: List<Schema<*>>
    get() = allOf.orEmpty() + oneOf.orEmpty() + anyOf.orEmpty()

/** The component a local `$ref` such as `#/components/schemas/Pet` names, or null for any other reference. */
internal fun String.componentName(section: String): String? =
    takeIf { it.startsWith("#/components/$section/") }?.removePrefix("#/components/$section/")

/** Visits every schema the spec defines, at every place it is written, without following `$ref`s. */
internal fun OpenAPI.walkSchemas(visit: (SchemaNode) -> Unit) = SpecWalker(visitSchema = visit).walk(this)

/** Every operation: in `paths`, webhooks, callbacks and component path items. */
internal fun OpenAPI.operations(): List<OperationNode> =
    mutableListOf<OperationNode>().also { operations -> SpecWalker(visitOperation = { operations.add(it) }).walk(this) }

/**
 * swagger-parser puts one Parameter, RequestBody, ApiResponse, Header, Callback or PathItem object into every place
 * that uses a 3.0 `$ref` or path-level parameter, so each is walked once, at its first location; components go first,
 * so a shared definition is named after itself. Schemas are walked at every place: in 3.1 the parser also shares one
 * object between equal inline schemas, and a check may depend on where a schema sits.
 */
private class SpecWalker(
    private val visitSchema: (SchemaNode) -> Unit = {},
    private val visitOperation: (OperationNode) -> Unit = {},
) {
    private val walked = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())

    fun walk(spec: OpenAPI) {
        spec.components?.run {
            schemas?.forEach { (name, schema) -> walk(SchemaNode(schema, name, componentName = name)) }
            parameters?.forEach { (name, parameter) -> parameter.walk("components.parameters.$name") }
            requestBodies?.forEach { (name, body) -> body.walk("components.requestBodies.$name") }
            responses?.forEach { (name, response) -> response.walk("components.responses.$name") }
            headers?.forEach { (name, header) -> header.walk("components.headers.$name") }
            callbacks?.forEach { (name, callback) -> callback.walk("components.callbacks.$name") }
            pathItems?.forEach { (name, item) -> item.walk("components.pathItems.$name") }
        }
        spec.paths?.forEach { (path, item) -> item.walk(path) }
        spec.webhooks?.forEach { (name, item) -> item.walk("webhooks.$name") }
    }

    private fun firstWalk(container: Any) = walked.add(container)

    private fun walk(node: SchemaNode) {
        // Only a cycle ends the walk; a schema shared between places is walked at each.
        if (generateSequence(node.parent) { it.parent }.any { it.schema === node.schema }) return
        visitSchema(node)
        val schema = node.schema
        val location = node.location
        fun child(child: Schema<*>?, location: String, role: SchemaRole = SchemaRole.NESTED) {
            if (child != null) walk(SchemaNode(child, location, node, role))
        }

        schema.properties?.forEach { (name, prop) -> child(prop, "$location.$name") }
        schema.patternProperties?.forEach { (pattern, prop) -> child(prop, "$location.patternProperties[$pattern]") }
        child(schema.additionalProperties as? Schema<*>, "$location.additionalProperties")
        child(schema.unevaluatedProperties, "$location.unevaluatedProperties")
        child(schema.propertyNames, "$location.propertyNames")
        child(schema.items, "$location[]")
        schema.prefixItems?.forEachIndexed { i, item -> child(item, "$location[$i]") }
        child(schema.additionalItems, "$location.additionalItems")
        child(schema.unevaluatedItems, "$location.unevaluatedItems")
        child(schema.contains, "$location.contains")
        schema.allOf?.forEachIndexed { i, member -> child(member, "$location.allOf[$i]", SchemaRole.APPLIED) }
        schema.oneOf?.forEachIndexed { i, member -> child(member, "$location.oneOf[$i]", SchemaRole.APPLIED) }
        schema.anyOf?.forEachIndexed { i, member -> child(member, "$location.anyOf[$i]", SchemaRole.APPLIED) }
        child(schema.`if`, "$location.if", SchemaRole.APPLIED)
        child(schema.then, "$location.then", SchemaRole.APPLIED)
        child(schema.`else`, "$location.else", SchemaRole.APPLIED)
        schema.dependentSchemas?.forEach { (name, dependent) ->
            child(dependent, "$location.dependentSchemas.$name", SchemaRole.APPLIED)
        }
        child(schema.not, "$location.not", SchemaRole.NEGATED)
    }

    private fun Content.walk(location: String) = forEach { (mediaType, media) ->
        media.schema?.let { walk(SchemaNode(it, "$location.$mediaType")) }
        media.encoding?.forEach { (property, encoding) ->
            encoding.headers?.forEach { (name, header) -> header.walk("$location.$mediaType.encoding.$property.headers.$name") }
        }
    }

    private fun walkSchemaOrContent(schema: Schema<*>?, content: Content?, location: String) {
        schema?.let { walk(SchemaNode(it, location)) }
        content?.walk(location)
    }

    private fun Parameter.walk(location: String) {
        if (firstWalk(this)) walkSchemaOrContent(schema, content, location)
    }

    private fun Header.walk(location: String) {
        if (firstWalk(this)) walkSchemaOrContent(schema, content, location)
    }

    private fun RequestBody.walk(location: String) {
        if (firstWalk(this)) content?.walk(location)
    }

    private fun ApiResponse.walk(location: String) {
        if (!firstWalk(this)) return
        content?.walk(location)
        headers?.forEach { (name, header) -> header.walk("$location.headers.$name") }
    }

    private fun Callback.walk(location: String) {
        if (firstWalk(this)) forEach { (expression, item) -> item.walk("$location.$expression") }
    }

    private fun PathItem.walk(location: String) {
        if (!firstWalk(this)) return
        parameters?.forEach { it.walk("$location.parameters.${it.name}") }
        readOperationsMap().forEach { (method, operation) ->
            val node = OperationNode(location, method, operation)
            visitOperation(node)
            operation.parameters?.forEach { it.walk("${node.location}.parameters.${it.name}") }
            operation.requestBody?.walk("${node.location}.requestBody")
            operation.responses?.forEach { (status, response) -> response.walk("${node.location}.responses.$status") }
            operation.callbacks?.forEach { (name, callback) -> callback.walk("${node.location}.callbacks.$name") }
        }
    }
}
