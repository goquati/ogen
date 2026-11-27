package de.quati.ogen.plugin.intern.oas.load

import de.quati.ogen.plugin.intern.oas.Diagnostic
import de.quati.ogen.plugin.intern.oas.OasVersion
import de.quati.ogen.plugin.intern.oas.SourceLocation
import de.quati.ogen.plugin.intern.oas.ref.JsonPointer
import de.quati.ogen.plugin.intern.oas.serial.OasSerialNames
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Migrates a document to OpenAPI 3.2 on its JSON tree. Only 3.0 → 3.1 changes anything beyond the version:
 * it rewrites the schemas at the given places (found by [OasWalker], so data such as examples is not touched).
 *
 * | 3.0 | 3.1 |
 * |---|---|
 * | `nullable: true` next to `type: X` | `type: [X, "null"]` |
 * | `nullable: true` without `type` | the type swagger-parser inferred (from `items`, `additionalProperties`, `enum`) plus `"null"` |
 * | `nullable: true` next to `oneOf`/`anyOf` | a `{type: "null"}` alternative |
 * | `nullable: true` next to `allOf` | `anyOf: [{allOf: …}, {type: "null"}]` |
 * | `nullable: true` with nothing to attach to | removed |
 * | `nullable: false` | removed |
 * | `minimum: n` + `exclusiveMinimum: true` | `exclusiveMinimum: n` (same for maximum) |
 * | `exclusiveMinimum: false` | removed |
 * | keywords next to `$ref` | removed, as 3.0 ignores them |
 *
 * `example` and `format: binary` stay: both are still valid, and code generation reads `format`. An `enum` of a
 * nullable schema gets no `null`: code generation reads `null` in an enum as a value of no string type, as
 * swagger-parser did, and would no longer generate an enum class.
 */
internal class OasMigration(
    private val from: OasVersion,
    private val locate: (JsonPointer, Boolean) -> SourceLocation,
) {
    val diagnostics = mutableListOf<Diagnostic>()

    /** The document with its schemas at [schemaPointers] migrated; with [isRoot], `openapi` is set to 3.2. */
    fun migrate(element: JsonElement, schemaPointers: Set<JsonPointer>, isRoot: Boolean): JsonElement {
        val migrated = if (from == OasVersion.V3_0 && schemaPointers.isNotEmpty()) {
            val onTheWay = schemaPointers.flatMapTo(mutableSetOf()) { pointer ->
                generateSequence(pointer) { it.parent }.toList()
            }
            rebuild(element, JsonPointer.ROOT, schemaPointers, onTheWay)
        } else element
        if (!isRoot || migrated !is JsonObject || "openapi" !in migrated) return migrated
        return JsonObject(migrated + ("openapi" to JsonPrimitive(TARGET_VERSION)))
    }

    // Bottom-up: inner schemas are migrated before the schema around them.
    private fun rebuild(element: JsonElement, pointer: JsonPointer, schemas: Set<JsonPointer>, onTheWay: Set<JsonPointer>): JsonElement {
        val rebuilt = when (element) {
            is JsonObject -> JsonObject(element.mapValues { (key, value) ->
                (pointer / key).let { if (it in onTheWay) rebuild(value, it, schemas, onTheWay) else value }
            })
            is JsonArray -> JsonArray(element.mapIndexed { index, value ->
                (pointer / index).let { if (it in onTheWay) rebuild(value, it, schemas, onTheWay) else value }
            })
            else -> element
        }
        return if (pointer in schemas && rebuilt is JsonObject) migrateSchema30(rebuilt, pointer) else rebuilt
    }

    private fun migrateSchema30(schema: JsonObject, pointer: JsonPointer): JsonObject {
        if (OasSerialNames.REF_KEY in schema) return dropRefSiblings(schema)
        val result = LinkedHashMap(schema)
        migrateExclusiveBound(result, pointer, "exclusiveMinimum", "minimum")
        migrateExclusiveBound(result, pointer, "exclusiveMaximum", "maximum")
        val nullable = result.remove("nullable")
        if ((nullable as? JsonPrimitive)?.booleanOrNull == true) migrateNullable(result, pointer)
        return JsonObject(result)
    }

    // 3.0 ignores everything next to `$ref` (the validator warns about it, see ignoredNextToRefWarnings).
    private fun dropRefSiblings(schema: JsonObject): JsonObject =
        JsonObject(schema.filterKeys { it == OasSerialNames.REF_KEY || OasSerialNames.isExtension(it) })

    private fun migrateExclusiveBound(schema: MutableMap<String, JsonElement>, pointer: JsonPointer, exclusive: String, bound: String) {
        val flag = (schema[exclusive] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull ?: return
        schema.remove(exclusive)
        if (!flag) return
        val value = schema.remove(bound)
        // Without the bound the flag has no effect (the validator warns about it).
        if (value != null) schema[exclusive] = value
    }

    private fun migrateNullable(schema: MutableMap<String, JsonElement>, pointer: JsonPointer) {
        val type = (schema["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: inferType(schema)
        val composition = COMPOSITIONS.firstOrNull { schema[it] is JsonArray }
        // Nothing to attach `null` to: `nullable` has no effect (the validator warns about it).
        if (type == null && composition == null) return
        if (type != null) schema["type"] = JsonArray(listOf(JsonPrimitive(type), JsonPrimitive("null")))
        when (composition) {
            "oneOf", "anyOf" -> {
                val alternatives = schema.getValue(composition) as JsonArray
                if (alternatives.none { it.isNullType() }) schema[composition] = JsonArray(alternatives + NULL_TYPE)
            }
            // allOf cannot take null as alternative; the whole allOf becomes one alternative to null.
            "allOf" -> if ("anyOf" !in schema) {
                val allOf = schema.getValue("allOf")
                val replaced = schema.entries.map { (key, value) ->
                    if (key == "allOf") "anyOf" to JsonArray(listOf(JsonObject(mapOf("allOf" to allOf)), NULL_TYPE))
                    else key to value
                }
                schema.clear()
                schema.putAll(replaced)
            }
        }
    }

    private fun JsonElement.isNullType() = (this as? JsonObject)?.get("type") == JsonPrimitive("null")

    private companion object {
        const val TARGET_VERSION = "3.2.0"
        val NULL_TYPE = JsonObject(mapOf("type" to JsonPrimitive("null")))
        val COMPOSITIONS = listOf("oneOf", "anyOf", "allOf")

        /**
         * The type swagger-parser infers in 3.0 when none is declared: `array` from `items`, else `object`
         * from `additionalProperties`, else the type of the first `enum` value.
         */
        fun inferType(schema: Map<String, JsonElement>): String? = when {
            "items" in schema -> "array"
            "additionalProperties" in schema -> "object"
            else -> (schema["enum"] as? JsonArray)?.let { values ->
                val first = values.firstOrNull() as? JsonPrimitive
                when {
                    values.isEmpty() -> "string"
                    first == null || first is JsonNull || first.isString -> "string"
                    first.booleanOrNull != null -> "boolean"
                    else -> "number"
                }
            }
        }
    }
}
