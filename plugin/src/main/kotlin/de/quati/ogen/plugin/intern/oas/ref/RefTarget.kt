package de.quati.ogen.plugin.intern.oas.ref

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URISyntaxException

/**
 * What a `$ref` points to: a document, identified by its absolute URI without fragment, and a JSON Pointer in it.
 * [toString] gives the absolute reference.
 */
internal data class RefTarget(val document: URI, val pointer: JsonPointer) {
    override fun toString(): String = "$document#${pointer.toUriFragment()}"

    companion object {
        /**
         * Resolves [ref] against [base]. The fragment is percent-decoded before it is read as JSON Pointer
         * (`#/components/schemas/My%20Schema`); characters a URI does not allow, as `{` in a path name, are
         * accepted. Throws [IllegalArgumentException] with a message for the user.
         */
        fun parse(ref: String, base: URI): RefTarget {
            val hash = ref.indexOf('#')
            val documentPart = if (hash < 0) ref else ref.substring(0, hash)
            val fragment = if (hash < 0) "" else UriText.decode(ref.substring(hash + 1))
            val document = if (documentPart.isEmpty()) base.canonical() else try {
                base.resolve(URI(UriText.encode(documentPart, UriText.URI_REFERENCE))).canonical()
            } catch (e: URISyntaxException) {
                throw IllegalArgumentException("'$ref' is not a valid URI reference: ${e.reason}")
            }
            val pointer = when {
                fragment.isEmpty() -> JsonPointer.ROOT
                fragment.startsWith("/") -> JsonPointer.parse(fragment)
                else -> throw IllegalArgumentException(
                    "'$ref' names an anchor ('#$fragment'); only JSON Pointer fragments are supported"
                )
            }
            return RefTarget(document, pointer)
        }
    }
}

/** The URI without fragment and normalized, so that equal documents get equal keys (`file:///a` is `file:/a`). */
internal fun URI.canonical(): URI {
    val text = toString().substringBefore('#').let { if (it.startsWith("file:///")) "file:/" + it.removePrefix("file:///") else it }
    val uri = URI(text)
    return if (uri.isOpaque) uri else uri.normalize()
}

/** The value at [pointer], or null if there is none. */
internal fun JsonElement.at(pointer: JsonPointer): JsonElement? = pointer.segments.fold(this as JsonElement?) { element, segment ->
    when (element) {
        is JsonObject -> element[segment]
        is JsonArray -> segment.takeIf { ARRAY_INDEX.matches(it) }?.toIntOrNull()?.let(element::getOrNull)
        else -> null
    }
}

/** A copy with the values at the given pointers replaced. */
internal fun JsonElement.replace(replacements: Map<JsonPointer, JsonElement>): JsonElement {
    if (replacements.isEmpty()) return this
    val byFirst = replacements.entries.groupBy({ it.key.segments.firstOrNull() }, { JsonPointer.of(*it.key.segments.drop(1).toTypedArray()) to it.value })
    byFirst[null]?.let { return it.single().second }
    return when (this) {
        is JsonObject -> JsonObject(mapValues { (key, value) -> byFirst[key]?.let { value.replace(it.toMap()) } ?: value })
        is JsonArray -> JsonArray(mapIndexed { index, value -> byFirst[index.toString()]?.let { value.replace(it.toMap()) } ?: value })
        else -> this
    }
}

private val ARRAY_INDEX = Regex("0|[1-9][0-9]*")

/** Percent-encoding and -decoding of URI text. */
internal object UriText {
    private const val UNRESERVED = "-._~"
    private const val SUB_DELIMS = "!$&'()*+,;="

    /** Characters a URI reference may contain as they are; `%` stays, as it starts an escape. */
    const val URI_REFERENCE = "$UNRESERVED$SUB_DELIMS:/?#[]@%"

    /** Characters a fragment may contain as they are; `%` is encoded, as a key may contain it. */
    const val FRAGMENT = "$UNRESERVED$SUB_DELIMS:@/?"

    fun encode(text: String, allowed: String): String = buildString {
        for (char in text) {
            if (char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char in allowed) append(char)
            else char.toString().toByteArray(Charsets.UTF_8).forEach { append('%').append("%02X".format(it.toInt() and 0xFF)) }
        }
    }

    /** Decodes valid `%XX` escapes as UTF-8; anything else stays as it is. */
    fun decode(text: String): String {
        if ('%' !in text) return text
        val result = StringBuilder()
        val bytes = ByteArrayOutputStream()
        fun flush() {
            if (bytes.size() > 0) result.append(bytes.toString(Charsets.UTF_8)).also { bytes.reset() }
        }
        var i = 0
        while (i < text.length) {
            val hex = if (text[i] == '%' && i + 2 <= text.lastIndex && text[i + 1].isHex() && text[i + 2].isHex())
                text.substring(i + 1, i + 3).toInt(16) else null
            if (hex != null) {
                bytes.write(hex)
                i += 3
            } else {
                flush()
                result.append(text[i])
                i++
            }
        }
        flush()
        return result.toString()
    }

    private fun Char.isHex() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}
