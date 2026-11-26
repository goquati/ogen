package de.quati.ogen.plugin.intern.validation

import de.quati.ogen.plugin.intern.model.HttpMethod
import de.quati.ogen.plugin.intern.oas.OasVersion
import de.quati.ogen.plugin.intern.oas.model.Callback
import de.quati.ogen.plugin.intern.oas.model.Content
import de.quati.ogen.plugin.intern.oas.model.Header
import de.quati.ogen.plugin.intern.oas.model.OpenApi
import de.quati.ogen.plugin.intern.oas.model.Operation
import de.quati.ogen.plugin.intern.oas.model.Parameter
import de.quati.ogen.plugin.intern.oas.model.PathItem
import de.quati.ogen.plugin.intern.oas.model.RefOr
import de.quati.ogen.plugin.intern.oas.model.RequestBody
import de.quati.ogen.plugin.intern.oas.model.Response
import de.quati.ogen.plugin.intern.oas.model.Schema
import de.quati.ogen.plugin.intern.parsing.operations

/**
 * The spec the checks read: bundled into one document (all references local) and migrated to OpenAPI 3.2.
 * [sourceVersion] is the version it was written in, for messages that name what the author wrote.
 */
internal class ValidatedSpec(val api: OpenApi, val sourceVersion: OasVersion) {
    val is30: Boolean get() = sourceVersion == OasVersion.V3_0
}

/** A variable in a path template, such as `{id}`; group 1 is its name. */
internal val pathVariable = Regex("""\{([^{}]+)}""")

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
 * Boolean schemas (`true`, `false`) have no keywords to check and are not visited.
 */
internal class SchemaNode(
    val schema: Schema.Obj,
    val location: String,
    val parent: SchemaNode? = null,
    val role: SchemaRole = SchemaRole.NESTED,
    /** Set on the root of a component schema. */
    val componentName: String? = null,
) {
    val isInsideNot: Boolean get() = role == SchemaRole.NEGATED || parent?.isInsideNot == true
}

/** A parameter; [ownerLocation] is where it is declared: a component, a path or an operation. */
internal class ParameterNode(val parameter: Parameter, val ownerLocation: String)

/** An operation; [pathItemLocation] is its path, or where its path item sits (`webhooks.newPet`, a callback). */
internal class OperationNode(val pathItemLocation: String, val method: HttpMethod, val operation: Operation) {
    val location get() = "$pathItemLocation:${method.name}"
}

internal val Schema.Obj.compositionMembers: List<Schema>
    get() = allOf.orEmpty() + oneOf.orEmpty() + anyOf.orEmpty()

/** The types a schema declares; after migration, 3.0's `nullable` is a `null` type. */
internal val Schema.Obj.declaredTypes: Set<String> get() = type?.toSet().orEmpty()

/** The component a local `$ref` such as `#/components/schemas/Pet` names, or null for any other reference. */
internal fun String.componentName(section: String): String? =
    takeIf { it.startsWith("#/components/$section/") }?.removePrefix("#/components/$section/")

/** Visits every schema the spec defines, at every place it is written, without following `$ref`s. */
internal fun ValidatedSpec.walkSchemas(visit: (SchemaNode) -> Unit) = SpecWalker(visitSchema = visit).walk(api)

/** Every operation: in `paths`, webhooks, callbacks and component path items. */
internal fun ValidatedSpec.operations(): List<OperationNode> =
    mutableListOf<OperationNode>().also { operations -> SpecWalker(visitOperation = { operations.add(it) }).walk(api) }

/** Every parameter, each once, where it is declared: components, path items and operations. */
internal fun ValidatedSpec.parameters(): List<ParameterNode> =
    mutableListOf<ParameterNode>().also { parameters -> SpecWalker(visitParameter = { parameters.add(it) }).walk(api) }

/**
 * Walks the spec, components first. A reference is not followed: what it names is walked where it is declared.
 */
private class SpecWalker(
    private val visitSchema: (SchemaNode) -> Unit = {},
    private val visitOperation: (OperationNode) -> Unit = {},
    private val visitParameter: (ParameterNode) -> Unit = {},
) {
    fun walk(spec: OpenApi) {
        spec.components?.run {
            schemas?.forEach { (name, schema) -> (schema as? Schema.Obj)?.let { walk(SchemaNode(it, name, componentName = name)) } }
            parameters?.forEach { (name, parameter) -> parameter.walkParameter("components.parameters.$name") }
            requestBodies?.forEach { (name, body) -> body.walkBody("components.requestBodies.$name") }
            responses?.forEach { (name, response) -> response.walkResponse("components.responses.$name") }
            headers?.forEach { (name, header) -> header.walkHeader("components.headers.$name") }
            callbacks?.forEach { (name, callback) -> callback.walkCallback("components.callbacks.$name") }
            pathItems?.forEach { (name, item) -> item.walk("components.pathItems.$name") }
        }
        spec.paths?.pathItems?.forEach { (path, item) -> item.walk(path) }
        spec.webhooks?.forEach { (name, item) -> item.walk("webhooks.$name") }
    }

    private fun walk(node: SchemaNode) {
        visitSchema(node)
        val schema = node.schema
        val location = node.location
        fun child(child: Schema?, location: String, role: SchemaRole = SchemaRole.NESTED) {
            if (child is Schema.Obj) walk(SchemaNode(child, location, node, role))
        }

        schema.properties?.forEach { (name, prop) -> child(prop, "$location.$name") }
        schema.patternProperties?.forEach { (pattern, prop) -> child(prop, "$location.patternProperties[$pattern]") }
        child(schema.additionalProperties, "$location.additionalProperties")
        child(schema.unevaluatedProperties, "$location.unevaluatedProperties")
        child(schema.propertyNames, "$location.propertyNames")
        child(schema.items, "$location[]")
        schema.prefixItems?.forEachIndexed { i, item -> child(item, "$location[$i]") }
        child(schema.unevaluatedItems, "$location.unevaluatedItems")
        child(schema.contains, "$location.contains")
        schema.allOf?.forEachIndexed { i, member -> child(member, "$location.allOf[$i]", SchemaRole.APPLIED) }
        schema.oneOf?.forEachIndexed { i, member -> child(member, "$location.oneOf[$i]", SchemaRole.APPLIED) }
        schema.anyOf?.forEachIndexed { i, member -> child(member, "$location.anyOf[$i]", SchemaRole.APPLIED) }
        child(schema.ifSchema, "$location.if", SchemaRole.APPLIED)
        child(schema.thenSchema, "$location.then", SchemaRole.APPLIED)
        child(schema.elseSchema, "$location.else", SchemaRole.APPLIED)
        schema.dependentSchemas?.forEach { (name, dependent) ->
            child(dependent, "$location.dependentSchemas.$name", SchemaRole.APPLIED)
        }
        child(schema.not, "$location.not", SchemaRole.NEGATED)
    }

    private fun Content.walk(location: String) = forEach { (mediaType, refOrMedia) ->
        val media = (refOrMedia as? RefOr.Value)?.value ?: return@forEach
        (media.schema as? Schema.Obj)?.let { walk(SchemaNode(it, "$location.$mediaType")) }
        media.encoding?.forEach { (property, encoding) ->
            encoding.headers?.forEach { (name, header) -> header.walkHeader("$location.$mediaType.encoding.$property.headers.$name") }
        }
    }

    private fun walkSchemaOrContent(schema: Schema?, content: Content?, location: String) {
        (schema as? Schema.Obj)?.let { walk(SchemaNode(it, location)) }
        content?.walk(location)
    }

    private fun RefOr<Parameter>.walkParameter(location: (Parameter) -> String, ownerLocation: String) {
        val parameter = (this as? RefOr.Value)?.value ?: return
        visitParameter(ParameterNode(parameter, ownerLocation))
        walkSchemaOrContent(parameter.schema, parameter.content, location(parameter))
    }

    private fun RefOr<Parameter>.walkParameter(location: String) = walkParameter({ location }, location)

    private fun RefOr<Header>.walkHeader(location: String) {
        val header = (this as? RefOr.Value)?.value ?: return
        walkSchemaOrContent(header.schema, header.content, location)
    }

    private fun RefOr<RequestBody>.walkBody(location: String) {
        (this as? RefOr.Value)?.value?.content?.walk(location)
    }

    private fun RefOr<Response>.walkResponse(location: String) {
        val response = (this as? RefOr.Value)?.value ?: return
        response.content?.walk(location)
        response.headers?.forEach { (name, header) -> header.walkHeader("$location.headers.$name") }
    }

    private fun RefOr<Callback>.walkCallback(location: String) {
        (this as? RefOr.Value)?.value?.pathItems?.forEach { (expression, item) -> item.walk("$location.$expression") }
    }

    private fun PathItem.walk(location: String) {
        parameters?.forEach { it.walkParameter({ p -> "$location.parameters.${p.name}" }, location) }
        operations().forEach { (method, operation) ->
            val node = OperationNode(location, method, operation)
            visitOperation(node)
            operation.parameters?.forEach { it.walkParameter({ p -> "${node.location}.parameters.${p.name}" }, node.location) }
            operation.requestBody?.walkBody("${node.location}.requestBody")
            operation.responses?.responses?.forEach { (status, response) -> response.walkResponse("${node.location}.responses.$status") }
            operation.callbacks?.forEach { (name, callback) -> callback.walkCallback("${node.location}.callbacks.$name") }
        }
    }
}
