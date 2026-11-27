package de.quati.ogen.plugin.intern.oas

/** The OpenAPI versions the parser reads; documents are migrated to the newest. */
internal enum class OasVersion(val minor: String) {
    V3_0("3.0"),
    V3_1("3.1"),
    V3_2("3.2");

    companion object {
        val LATEST = V3_2

        /** The version of the `openapi` field, e.g. `3.0.3` or `3.1`; null if it is none of the supported. */
        fun parse(openapi: String): OasVersion? {
            val match = VERSION.matchEntire(openapi.trim()) ?: return null
            return entries.find { it.minor == match.groupValues[1] }
        }

        private val VERSION = Regex("""(\d+\.\d+)(\.\d+)?(-[0-9A-Za-z.-]+)?""")
    }
}
