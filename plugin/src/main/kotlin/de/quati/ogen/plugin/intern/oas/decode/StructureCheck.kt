package de.quati.ogen.plugin.intern.oas.decode

import de.quati.ogen.plugin.intern.oas.Diagnostic
import de.quati.ogen.plugin.intern.oas.OasVersion
import de.quati.ogen.plugin.intern.oas.SourceLocation
import de.quati.ogen.plugin.intern.oas.ref.JsonPointer
import de.quati.ogen.plugin.intern.oas.serial.OasRequired
import de.quati.ogen.plugin.intern.oas.serial.OasSerialNames
import de.quati.ogen.plugin.intern.oas.serial.OasSince
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Checks a JSON tree against the model's serial descriptors before it is decoded, and collects every problem
 * with its location: unknown fields, missing required fields, values of the wrong type, unknown enum values.
 * kotlinx.serialization itself stops at the first problem and knows no positions.
 *
 * Lenient where swagger-parser was: a number or boolean is accepted where a string is expected
 * (`version: 1.0`), and `null` counts as absent for optional fields.
 *
 * [version] is the one the spec was written in: a field added later ([OasSince]) is unknown in it.
 */
internal class StructureCheck(
    private val version: OasVersion,
    private val locate: (pointer: JsonPointer, key: Boolean) -> SourceLocation,
) {
    private val diagnostics = mutableListOf<Diagnostic>()

    /** The values of the last check that do not fit the model; decoding leaves them out, as swagger-parser did. */
    val invalidValues = mutableListOf<JsonPointer>()

    fun check(element: JsonElement, descriptor: SerialDescriptor, pointer: JsonPointer = JsonPointer.ROOT): List<Diagnostic> {
        diagnostics.clear()
        invalidValues.clear()
        checkValue(element, descriptor, pointer)
        return diagnostics.toList()
    }

    // Problems with a key (unknown, ignored, malformed) point to the key, all others to the value.
    private fun error(pointer: JsonPointer, message: String, key: Boolean = false) {
        diagnostics += Diagnostic.error(message, locate(pointer, key))
    }

    private fun checkValue(element: JsonElement, descriptor: SerialDescriptor, pointer: JsonPointer) {
        val name = descriptor.serialName.removeSuffix("?")
        if (element is JsonNull) {
            if (!descriptor.isNullable && name != OasSerialNames.ANY_VALUE && name != JSON_ELEMENT)
                error(pointer, "expected ${expected(descriptor)}, found null")
            return
        }
        when {
            name == OasSerialNames.ANY_VALUE || name == JSON_ELEMENT -> Unit
            name.startsWith(OasSerialNames.REF_OR) -> checkRefOr(element, descriptor, pointer)
            name == OasSerialNames.SCHEMA -> checkSchema(element, descriptor, pointer)
            name == OasSerialNames.STRING_OR_LIST -> checkStringOrList(element, pointer)
            name == OasSerialNames.BIG_DECIMAL -> if (!element.isNumber()) mismatch(pointer, "a number", element)
            name.startsWith(OasSerialNames.EXTENSIBLE_MAP) -> checkExtensibleMap(element, descriptor, pointer)
            else -> checkByKind(element, descriptor, pointer)
        }
    }

    private fun checkByKind(element: JsonElement, descriptor: SerialDescriptor, pointer: JsonPointer) {
        when (val kind = descriptor.kind) {
            PrimitiveKind.STRING -> if (element !is JsonPrimitive) mismatch(pointer, "a string", element)
            PrimitiveKind.BOOLEAN ->
                if (element !is JsonPrimitive || element.isString || element.booleanOrNull == null)
                    mismatch(pointer, "a boolean", element)
            PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG ->
                if (!element.isNumber() || !INTEGER.matches((element as JsonPrimitive).content))
                    mismatch(pointer, "an integer", element)
            PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> if (!element.isNumber()) mismatch(pointer, "a number", element)
            SerialKind.ENUM -> {
                val allowed = descriptor.elementNames.toList()
                if (element !is JsonPrimitive || !element.isString && element.booleanOrNull != null)
                    mismatch(pointer, "one of ${allowed.joinToString()}", element)
                else if (element.content !in allowed) {
                    error(pointer, "'${element.content}' is not one of ${allowed.joinToString()}")
                    invalidValues += pointer
                }
            }
            StructureKind.CLASS -> checkObject(element, descriptor, pointer, allowUnknown = false)
            StructureKind.LIST -> {
                if (element !is JsonArray) return mismatch(pointer, "an array", element)
                val itemDescriptor = descriptor.getElementDescriptor(0)
                element.forEachIndexed { index, item -> checkValue(item, itemDescriptor, pointer / index) }
            }
            StructureKind.MAP -> {
                if (element !is JsonObject) return mismatch(pointer, "an object", element)
                val valueDescriptor = descriptor.getElementDescriptor(1)
                element.forEach { (key, value) -> checkValue(value, valueDescriptor, pointer / key) }
            }
            else -> error("unsupported descriptor kind $kind of ${descriptor.serialName}")
        }
    }

    private fun checkObject(element: JsonElement, descriptor: SerialDescriptor, pointer: JsonPointer, allowUnknown: Boolean) {
        if (element !is JsonObject) return mismatch(pointer, "${typeName(descriptor)} object", element)
        val type = typeName(descriptor)
        val extensible = OasSerialNames.EXTENSIONS_KEY in descriptor.elementNames
        element.forEach { (key, value) ->
            val index = descriptor.getElementIndex(key)
            when {
                key == OasSerialNames.EXTENSIONS_KEY || key == OasSerialNames.OTHER_KEY ->
                    if (!allowUnknown) error(pointer / key, "unknown field '$key' in $type", key = true)
                extensible && OasSerialNames.isExtension(key) -> Unit
                index >= 0 && descriptor.since(index) > version -> {
                    val since = descriptor.since(index).minor
                    error(pointer / key, "unknown field '$key' in $type of OpenAPI ${version.minor} (added in $since)", key = true)
                    invalidValues += pointer / key
                }
                index >= 0 -> checkValue(value, descriptor.getElementDescriptor(index), pointer / key)
                !allowUnknown -> error(pointer / key, "unknown field '$key' in $type", key = true)
            }
        }
        for (index in 0 until descriptor.elementsCount) {
            if (descriptor.since(index) > version) continue
            val key = descriptor.getElementName(index)
            val required = !descriptor.isElementOptional(index) || descriptor.getElementAnnotations(index).any { it is OasRequired }
            if (required && element[key].let { it == null || it is JsonNull })
                error(pointer, "missing required field '$key' in $type")
        }
    }

    private fun checkRefOr(element: JsonElement, descriptor: SerialDescriptor, pointer: JsonPointer) {
        if (element !is JsonObject || OasSerialNames.REF_KEY !in element)
            return checkValue(element, descriptor.getElementDescriptor(descriptor.getElementIndex("value")), pointer)
        element.forEach { (key, value) ->
            when (key) {
                OasSerialNames.REF_KEY, "summary", "description" ->
                    if (value !is JsonPrimitive || value is JsonNull || value.isString.not())
                        mismatch(pointer / key, "a string", value)
                // Ignored, as the specification says; the validator warns about it.
                else -> Unit
            }
        }
    }

    private fun checkSchema(element: JsonElement, descriptor: SerialDescriptor, pointer: JsonPointer) {
        if (element is JsonPrimitive && !element.isString && element.booleanOrNull != null) return
        if (element !is JsonObject) return mismatch(pointer, "a schema (an object or a boolean)", element)
        checkObject(element, descriptor.getElementDescriptor(0), pointer, allowUnknown = true)
    }

    private fun checkStringOrList(element: JsonElement, pointer: JsonPointer) {
        when (element) {
            is JsonPrimitive -> if (!element.isString) mismatch(pointer, "a string or an array of strings", element)
            is JsonArray -> element.forEachIndexed { index, item ->
                if (item !is JsonPrimitive || !item.isString || item is JsonNull) mismatch(pointer / index, "a string", item)
            }
            else -> mismatch(pointer, "a string or an array of strings", element)
        }
    }

    private fun checkExtensibleMap(element: JsonElement, descriptor: SerialDescriptor, pointer: JsonPointer) {
        if (element !is JsonObject) return mismatch(pointer, "an object", element)
        val kind = descriptor.serialName.removeSuffix("?").substringAfter('<').removeSuffix(">")
        val valueDescriptor = descriptor.getElementDescriptor(0)
        element.forEach { (key, value) ->
            if (OasSerialNames.isExtension(key)) return@forEach
            when (kind) {
                "Paths" -> if (!key.startsWith("/")) error(pointer / key, "path '$key' must start with '/'", key = true)
                "Responses" -> if (key != "default" && !STATUS_CODE.matches(key))
                    error(pointer / key, "'$key' is not a status code like 200 or 4XX, nor 'default'", key = true)
            }
            checkValue(value, valueDescriptor, pointer / key)
        }
    }

    private fun mismatch(pointer: JsonPointer, expected: String, found: JsonElement) {
        error(pointer, "expected $expected, found ${describe(found)}")
        invalidValues += pointer
    }

    private fun expected(descriptor: SerialDescriptor) = when (descriptor.kind) {
        PrimitiveKind.STRING -> "a string"
        PrimitiveKind.BOOLEAN -> "a boolean"
        StructureKind.LIST -> "an array"
        StructureKind.MAP -> "an object"
        else -> "a value"
    }

    private fun SerialDescriptor.since(index: Int) =
        getElementAnnotations(index).filterIsInstance<OasSince>().firstOrNull()?.version ?: OasVersion.V3_0

    private fun typeName(descriptor: SerialDescriptor) =
        descriptor.serialName.removeSuffix("?").substringAfterLast('.').substringAfterLast('$')

    private fun JsonElement.isNumber() =
        this is JsonPrimitive && this !is JsonNull && !isString && booleanOrNull == null

    private fun describe(element: JsonElement) = when {
        element is JsonObject -> "an object"
        element is JsonArray -> "an array"
        element is JsonNull -> "null"
        element is JsonPrimitive && element.isString -> "the string '${element.content}'"
        element is JsonPrimitive && element.booleanOrNull != null -> "the boolean ${element.content}"
        else -> "the number ${(element as JsonPrimitive).content}"
    }

    private companion object {
        const val JSON_ELEMENT = "kotlinx.serialization.json.JsonElement"
        val INTEGER = Regex("-?[0-9]+")
        // Case-insensitive like HttpCode.parse: ogen accepts "2xx" although the specification writes "2XX".
        val STATUS_CODE = Regex("[1-5](?:[0-9]{2}|XX)", RegexOption.IGNORE_CASE)
    }
}
