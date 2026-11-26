package de.quati.ogen.plugin.intern.oas.ref

/**
 * A JSON Pointer (RFC 6901) as a list of decoded reference tokens.
 * [toString] gives the escaped form (`/paths/~1users`), without the `#` of a URI fragment.
 */
@JvmInline
internal value class JsonPointer private constructor(val segments: List<String>) {
    operator fun div(segment: String): JsonPointer = JsonPointer(segments + segment)
    operator fun div(index: Int): JsonPointer = div(index.toString())

    val parent: JsonPointer? get() = if (segments.isEmpty()) null else JsonPointer(segments.dropLast(1))
    val isRoot: Boolean get() = segments.isEmpty()

    override fun toString(): String = segments.joinToString(separator = "") { "/" + escape(it) }

    companion object {
        val ROOT: JsonPointer = JsonPointer(emptyList())

        fun of(vararg segments: String): JsonPointer = JsonPointer(segments.toList())

        /** Parses the escaped form; an empty string is the root. */
        fun parse(value: String): JsonPointer {
            if (value.isEmpty()) return ROOT
            require(value.startsWith("/")) { "JSON Pointer '$value' must be empty or start with '/'" }
            return JsonPointer(value.substring(1).split("/").map { unescape(it, value) })
        }

        private fun escape(segment: String) = segment.replace("~", "~0").replace("/", "~1")

        // '~1' is decoded before '~0', so '~01' stays the literal '~1'
        private fun unescape(segment: String, pointer: String): String {
            val invalid = Regex("~(?![01])")
            require(!invalid.containsMatchIn(segment)) { "JSON Pointer '$pointer' has a '~' that is not followed by '0' or '1'" }
            return segment.replace("~1", "/").replace("~0", "~")
        }
    }
}
