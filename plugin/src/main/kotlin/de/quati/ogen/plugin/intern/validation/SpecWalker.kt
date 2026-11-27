package de.quati.ogen.plugin.intern.validation

import de.quati.ogen.plugin.intern.model.HttpMethod
import de.quati.ogen.plugin.intern.oas.OasVersion
import de.quati.ogen.plugin.intern.oas.load.Bundle
import de.quati.ogen.plugin.intern.oas.ref.JsonPointer
import kotlinx.serialization.json.JsonObject
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
internal class ValidatedSpec(val api: OpenApi, val sourceVersion: OasVersion, val bundle: Bundle? = null) {
    val is30: Boolean get() = sourceVersion == OasVersion.V3_0

    /** The object at [pointer] (a place in the bundle) as its file has it, before migration; null if unknown. */
    fun source(pointer: JsonPointer): JsonObject? = bundle?.source(pointer) as? JsonObject

    /** Where [pointer] is in the spec's files, for messages: `oas.yaml:12:5`. */
    fun position(pointer: JsonPointer): String? = bundle?.locate(pointer)?.let { location ->
        location.position?.let { "${location.source}:$it" } ?: location.source
    }
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
    /** Its place in the bundle, as written: for a 3.0 spec without the nesting migration added. */
    val pointer: JsonPointer = JsonPointer.ROOT,
    /** The schema as its file has it, before migration; null for a spec built in code. */
    val source: JsonObject? = null,
) {
    val isInsideNot: Boolean get() = role == SchemaRole.NEGATED || parent?.isInsideNot == true
}

/** A parameter; [ownerLocation] is where it is declared: a component, a path or an operation. */
internal class ParameterNode(val parameter: Parameter, val ownerLocation: String)

/** A Reference Object (`$ref` to a parameter, response, …) at [location]; [source] is the object as written. */
internal class ReferenceNode(val location: String, val pointer: JsonPointer, val source: JsonObject?)

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
internal fun ValidatedSpec.walkSchemas(visit: (SchemaNode) -> Unit) = SpecWalker(this, visitSchema = visit).walk()

/** Every operation: in `paths`, webhooks, callbacks and component path items. */
internal fun ValidatedSpec.operations(): List<OperationNode> =
    mutableListOf<OperationNode>().also { operations -> SpecWalker(this, visitOperation = { operations.add(it) }).walk() }

/** Every parameter, each once, where it is declared: components, path items and operations. */
internal fun ValidatedSpec.parameters(): List<ParameterNode> =
    mutableListOf<ParameterNode>().also { parameters -> SpecWalker(this, visitParameter = { parameters.add(it) }).walk() }

/** Every Reference Object outside schemas: of parameters, request bodies, responses, headers, callbacks, media types. */
internal fun ValidatedSpec.references(): List<ReferenceNode> =
    mutableListOf<ReferenceNode>().also { references -> SpecWalker(this, visitReference = { references.add(it) }).walk() }

private val NULL_ALTERNATIVE = Schema.Obj(type = listOf("null"))

/**
 * A 3.0 schema as written: migration made a nullable `allOf` into `anyOf: [{allOf: …}, {type: null}]` and gave a
 * nullable `oneOf`/`anyOf` a `{type: null}` alternative; 3.0 has no null type, so both come from migration.
 */
private fun Schema.Obj.asWritten30(): Schema.Obj {
    var schema = this
    val alternatives = schema.anyOf
    if (schema.allOf == null && alternatives != null && alternatives.size == 2 && NULL_ALTERNATIVE in alternatives) {
        val inner = alternatives.first { it != NULL_ALTERNATIVE } as? Schema.Obj
        if (inner?.allOf != null && inner == Schema.Obj(allOf = inner.allOf)) schema = schema.copy(anyOf = null, allOf = inner.allOf)
    }
    schema.oneOf?.takeIf { NULL_ALTERNATIVE in it }?.let { schema = schema.copy(oneOf = it.filter { s -> s != NULL_ALTERNATIVE }) }
    schema.anyOf?.takeIf { NULL_ALTERNATIVE in it }?.let { schema = schema.copy(anyOf = it.filter { s -> s != NULL_ALTERNATIVE }) }
    return schema
}

/**
 * Walks the spec, components first. A reference is not followed: what it names is walked where it is declared.
 * Each node knows its place in the bundle, so a check can read what its file has there.
 */
private class SpecWalker(
    private val spec: ValidatedSpec,
    private val visitSchema: (SchemaNode) -> Unit = {},
    private val visitOperation: (OperationNode) -> Unit = {},
    private val visitParameter: (ParameterNode) -> Unit = {},
    private val visitReference: (ReferenceNode) -> Unit = {},
) {
    fun walk() {
        val root = JsonPointer.ROOT
        spec.api.components?.run {
            val components = root / "components"
            schemas?.forEach { (name, schema) -> schema(schema, name, components / "schemas" / name, componentName = name) }
            parameters?.forEach { (name, parameter) ->
                parameter.walkParameter({ "components.parameters.$name" }, "components.parameters.$name", components / "parameters" / name)
            }
            requestBodies?.forEach { (name, body) -> body.walkBody("components.requestBodies.$name", components / "requestBodies" / name) }
            responses?.forEach { (name, response) -> response.walkResponse("components.responses.$name", components / "responses" / name) }
            headers?.forEach { (name, header) -> header.walkHeader("components.headers.$name", components / "headers" / name) }
            callbacks?.forEach { (name, callback) -> callback.walkCallback("components.callbacks.$name", components / "callbacks" / name) }
            pathItems?.forEach { (name, item) -> item.walk("components.pathItems.$name", components / "pathItems" / name) }
        }
        spec.api.paths?.pathItems?.forEach { (path, item) -> item.walk(path, root / "paths" / path) }
        spec.api.webhooks?.forEach { (name, item) -> item.walk("webhooks.$name", root / "webhooks" / name) }
    }

    private fun schema(
        schema: Schema?,
        location: String,
        pointer: JsonPointer,
        parent: SchemaNode? = null,
        role: SchemaRole = SchemaRole.NESTED,
        componentName: String? = null,
    ) {
        if (schema !is Schema.Obj) return
        val written = if (spec.is30) schema.asWritten30() else schema
        walk(SchemaNode(written, location, parent, role, componentName, pointer, spec.source(pointer)))
    }

    private fun walk(node: SchemaNode) {
        visitSchema(node)
        val schema = node.schema
        val location = node.location
        val pointer = node.pointer
        fun child(child: Schema?, location: String, at: JsonPointer, role: SchemaRole = SchemaRole.NESTED) =
            schema(child, location, at, node, role)

        schema.properties?.forEach { (name, prop) -> child(prop, "$location.$name", pointer / "properties" / name) }
        schema.patternProperties?.forEach { (pattern, prop) ->
            child(prop, "$location.patternProperties[$pattern]", pointer / "patternProperties" / pattern)
        }
        child(schema.additionalProperties, "$location.additionalProperties", pointer / "additionalProperties")
        child(schema.unevaluatedProperties, "$location.unevaluatedProperties", pointer / "unevaluatedProperties")
        child(schema.propertyNames, "$location.propertyNames", pointer / "propertyNames")
        child(schema.items, "$location[]", pointer / "items")
        schema.prefixItems?.forEachIndexed { i, item -> child(item, "$location[$i]", pointer / "prefixItems" / i) }
        child(schema.unevaluatedItems, "$location.unevaluatedItems", pointer / "unevaluatedItems")
        child(schema.contains, "$location.contains", pointer / "contains")
        schema.allOf?.forEachIndexed { i, member -> child(member, "$location.allOf[$i]", pointer / "allOf" / i, SchemaRole.APPLIED) }
        schema.oneOf?.forEachIndexed { i, member -> child(member, "$location.oneOf[$i]", pointer / "oneOf" / i, SchemaRole.APPLIED) }
        schema.anyOf?.forEachIndexed { i, member -> child(member, "$location.anyOf[$i]", pointer / "anyOf" / i, SchemaRole.APPLIED) }
        child(schema.ifSchema, "$location.if", pointer / "if", SchemaRole.APPLIED)
        child(schema.thenSchema, "$location.then", pointer / "then", SchemaRole.APPLIED)
        child(schema.elseSchema, "$location.else", pointer / "else", SchemaRole.APPLIED)
        schema.dependentSchemas?.forEach { (name, dependent) ->
            child(dependent, "$location.dependentSchemas.$name", pointer / "dependentSchemas" / name, SchemaRole.APPLIED)
        }
        child(schema.not, "$location.not", pointer / "not", SchemaRole.NEGATED)
        // Definitions describe no value of the parent's, so they are walked without one, like components.
        schema.defs?.forEach { (name, def) -> schema(def, "$location.\$defs.$name", pointer / "\$defs" / name) }
    }

    // A reference is reported, not walked; true if [this] is one.
    private fun RefOr<*>.isReference(location: String, pointer: JsonPointer): Boolean {
        if (this !is RefOr.Ref) return false
        visitReference(ReferenceNode(location, pointer, spec.source(pointer)))
        return true
    }

    private fun Content.walk(location: String, pointer: JsonPointer) = forEach { (mediaType, refOrMedia) ->
        val at = pointer / mediaType
        if (refOrMedia.isReference("$location.$mediaType", at)) return@forEach
        val media = (refOrMedia as RefOr.Value).value
        schema(media.schema, "$location.$mediaType", at / "schema")
        media.encoding?.forEach { (property, encoding) ->
            encoding.headers?.forEach { (name, header) ->
                header.walkHeader("$location.$mediaType.encoding.$property.headers.$name", at / "encoding" / property / "headers" / name)
            }
        }
    }

    private fun walkSchemaOrContent(schema: Schema?, content: Content?, location: String, pointer: JsonPointer) {
        schema(schema, location, pointer / "schema")
        content?.walk(location, pointer / "content")
    }

    private fun RefOr<Parameter>.walkParameter(location: (Parameter) -> String, ownerLocation: String, pointer: JsonPointer) {
        if (isReference(ownerLocation, pointer)) return
        val parameter = (this as RefOr.Value).value
        visitParameter(ParameterNode(parameter, ownerLocation))
        walkSchemaOrContent(parameter.schema, parameter.content, location(parameter), pointer)
    }

    private fun RefOr<Header>.walkHeader(location: String, pointer: JsonPointer) {
        if (isReference(location, pointer)) return
        val header = (this as RefOr.Value).value
        walkSchemaOrContent(header.schema, header.content, location, pointer)
    }

    private fun RefOr<RequestBody>.walkBody(location: String, pointer: JsonPointer) {
        if (isReference(location, pointer)) return
        (this as RefOr.Value).value.content?.walk(location, pointer / "content")
    }

    private fun RefOr<Response>.walkResponse(location: String, pointer: JsonPointer) {
        if (isReference(location, pointer)) return
        val response = (this as RefOr.Value).value
        response.content?.walk(location, pointer / "content")
        response.headers?.forEach { (name, header) -> header.walkHeader("$location.headers.$name", pointer / "headers" / name) }
    }

    private fun RefOr<Callback>.walkCallback(location: String, pointer: JsonPointer) {
        if (isReference(location, pointer)) return
        (this as RefOr.Value).value.pathItems.forEach { (expression, item) -> item.walk("$location.$expression", pointer / expression) }
    }

    private fun PathItem.walk(location: String, pointer: JsonPointer) {
        parameters?.forEachIndexed { i, parameter ->
            parameter.walkParameter({ p -> "$location.parameters.${p.name}" }, location, pointer / "parameters" / i)
        }
        operations().forEach { (method, operation) ->
            val node = OperationNode(location, method, operation)
            val at = pointer / method.fieldName
            visitOperation(node)
            operation.parameters?.forEachIndexed { i, parameter ->
                parameter.walkParameter({ p -> "${node.location}.parameters.${p.name}" }, node.location, at / "parameters" / i)
            }
            operation.requestBody?.walkBody("${node.location}.requestBody", at / "requestBody")
            operation.responses?.responses?.forEach { (status, response) ->
                response.walkResponse("${node.location}.responses.$status", at / "responses" / status)
            }
            operation.callbacks?.forEach { (name, callback) -> callback.walkCallback("${node.location}.callbacks.$name", at / "callbacks" / name) }
        }
    }
}
