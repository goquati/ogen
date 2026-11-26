package de.quati.ogen.plugin.intern.oas.load

import de.quati.ogen.plugin.intern.oas.model.Discriminator
import de.quati.ogen.plugin.intern.oas.model.SchemaSerializer
import de.quati.ogen.plugin.intern.oas.ref.JsonPointer
import de.quati.ogen.plugin.intern.oas.ref.canonical
import de.quati.ogen.plugin.intern.oas.serial.OasSerialNames
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI

/**
 * Walks a JSON tree along the model's serial descriptors, so that it knows at each place what is expected.
 * It reports references with the type they must point to, and the schema resources (`$id`) it passes.
 * Values that are data, not OAS structure — `example`, `default`, `enum`, `const`, extensions — are not entered,
 * so a `$ref` in an example stays a value.
 */
internal abstract class OasWalker {
    /**
     * A reference at [pointer] (the `$ref` string itself, or a discriminator mapping value) that must point to
     * something of [expected]; [base] is the URI it is relative to.
     */
    protected abstract fun onRef(pointer: JsonPointer, ref: String, expected: SerialDescriptor, base: URI)

    /** A schema with `$id` at [pointer], identified by [uri]. */
    protected open fun onSchemaResource(pointer: JsonPointer, uri: URI) {}

    fun walk(element: JsonElement, descriptor: SerialDescriptor, pointer: JsonPointer, base: URI) {
        val name = descriptor.serialName.removeSuffix("?")
        when {
            name == OasSerialNames.ANY_VALUE || name == JSON_ELEMENT -> Unit
            name.startsWith(OasSerialNames.REF_OR) -> {
                val valueDescriptor = descriptor.getElementDescriptor(descriptor.getElementIndex("value"))
                val ref = element.refOrNull()
                if (ref != null) onRef(pointer / OasSerialNames.REF_KEY, ref, valueDescriptor, base)
                else walk(element, valueDescriptor, pointer, base)
            }
            name == OasSerialNames.SCHEMA -> walkSchema(element, descriptor.getElementDescriptor(0), pointer, base)
            name.startsWith(OasSerialNames.EXTENSIBLE_MAP) -> (element as? JsonObject)?.forEach { (key, value) ->
                if (!OasSerialNames.isExtension(key)) walk(value, descriptor.getElementDescriptor(0), pointer / key, base)
            }
            descriptor.kind == StructureKind.CLASS -> walkObject(element, descriptor, pointer, base, ownRef = true)
            descriptor.kind == StructureKind.LIST -> (element as? kotlinx.serialization.json.JsonArray)?.forEachIndexed { index, item ->
                walk(item, descriptor.getElementDescriptor(0), pointer / index, base)
            }
            descriptor.kind == StructureKind.MAP -> (element as? JsonObject)?.forEach { (key, value) ->
                walk(value, descriptor.getElementDescriptor(1), pointer / key, base)
            }
        }
    }

    private fun walkObject(element: JsonElement, descriptor: SerialDescriptor, pointer: JsonPointer, base: URI, ownRef: Boolean) {
        val obj = element as? JsonObject ?: return
        // A Path Item has a `$ref` field of its own.
        if (ownRef && descriptor.getElementIndex(OasSerialNames.REF_KEY) >= 0)
            obj.refOrNull()?.let { onRef(pointer / OasSerialNames.REF_KEY, it, descriptor, base) }
        obj.forEach { (key, value) ->
            val index = descriptor.getElementIndex(key)
            if (index < 0 || key in SKIPPED_KEYS) return@forEach
            walk(value, descriptor.getElementDescriptor(index), pointer / key, base)
        }
        // Mapping values are schema names or references; a reference contains '#' or '/'.
        if (descriptor.serialName.removeSuffix("?") == DISCRIMINATOR)
            (obj["mapping"] as? JsonObject)?.forEach { (key, value) ->
                val ref = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@forEach
                if ('#' in ref || '/' in ref) onRef(pointer / "mapping" / key, ref, SCHEMA, base)
            }
    }

    private fun walkSchema(element: JsonElement, objDescriptor: SerialDescriptor, pointer: JsonPointer, base: URI) {
        val obj = element as? JsonObject ?: return
        // `$id` sets the base for the schema's own `$ref` and for everything inside it.
        val schemaBase = obj.stringOrNull(ID_KEY)
            ?.let { runCatching { base.resolve(URI(it)).canonical() }.getOrNull() }
            ?.also { onSchemaResource(pointer, it) }
            ?: base
        obj.refOrNull()?.let { onRef(pointer / OasSerialNames.REF_KEY, it, SCHEMA, schemaBase) }
        walkObject(obj, objDescriptor, pointer, schemaBase, ownRef = false)
        // Keywords without a field of their own that hold subschemas.
        obj.forEach { (key, value) ->
            when (key) {
                in SUBSCHEMA_KEYWORDS -> walk(value, SCHEMA, pointer / key, schemaBase)
                in SUBSCHEMA_MAP_KEYWORDS -> (value as? JsonObject)?.forEach { (name, schema) ->
                    walk(schema, SCHEMA, pointer / key / name, schemaBase)
                }
            }
        }
    }

    private fun JsonElement.refOrNull() = (this as? JsonObject)?.stringOrNull(OasSerialNames.REF_KEY)
    private fun JsonObject.stringOrNull(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private companion object {
        const val JSON_ELEMENT = "kotlinx.serialization.json.JsonElement"
        const val ID_KEY = $$"$id"
        val SCHEMA: SerialDescriptor get() = SchemaSerializer.descriptor
        val DISCRIMINATOR = Discriminator.serializer().descriptor.serialName
        val SKIPPED_KEYS = setOf(OasSerialNames.EXTENSIONS_KEY, OasSerialNames.OTHER_KEY, OasSerialNames.REF_KEY)
        val SUBSCHEMA_KEYWORDS = setOf(
            "if", "then", "else", "contains", "propertyNames", "unevaluatedItems", "unevaluatedProperties",
            "contentSchema", "additionalItems",
        )
        val SUBSCHEMA_MAP_KEYWORDS = setOf("dependentSchemas")
    }
}
