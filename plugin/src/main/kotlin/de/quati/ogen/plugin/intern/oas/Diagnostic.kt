package de.quati.ogen.plugin.intern.oas

import de.quati.ogen.plugin.intern.oas.ref.JsonPointer

/** A line and column in a source file, both 1-based. */
internal data class SourcePosition(val line: Int, val column: Int) {
    override fun toString(): String = "$line:$column"
}

/** Where a diagnostic points to: the file, and inside it the JSON Pointer and, if known, the position. */
internal data class SourceLocation(
    val source: String,
    val pointer: JsonPointer? = null,
    val position: SourcePosition? = null,
) {
    override fun toString(): String = buildString {
        append(source)
        position?.let { append(":$it") }
        pointer?.let { append(" (#$it)") }
    }
}

/** A problem found while reading, loading or decoding a spec; collected instead of thrown. */
internal data class Diagnostic(
    val severity: Severity,
    val message: String,
    val location: SourceLocation?,
) {
    enum class Severity { ERROR, WARNING }

    override fun toString(): String = location?.let { "$it: $message" } ?: message

    companion object {
        fun error(message: String, location: SourceLocation?) = Diagnostic(Severity.ERROR, message, location)
        fun warning(message: String, location: SourceLocation?) = Diagnostic(Severity.WARNING, message, location)
    }
}
