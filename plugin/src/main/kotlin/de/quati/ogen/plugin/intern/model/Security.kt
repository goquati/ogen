package de.quati.ogen.plugin.intern.model

internal data class Security(
    val data: Set<Set<ComponentName.Security>>,
) {
    val anySecurity get() = data.isNotEmpty()
    val noSecurity get() = data.isEmpty()
}