package de.quati.ogen.plugin.intern.model

internal data class Security(
    val data: List<List<RequirementObject>>,
) {
    val anySecurity get() = data.any { it.isNotEmpty() }
    val noSecurity get() = data.all { it.isEmpty() }

    data class RequirementObject(
        val name: ComponentName.Security,
        val type: SecuritySchemeType,
    )
}

/** The type of a Security Scheme; [value] is how the spec writes it. */
internal enum class SecuritySchemeType(val value: String) {
    API_KEY("apiKey"),
    HTTP("http"),
    MUTUAL_TLS("mutualTLS"),
    OAUTH2("oauth2"),
    OPEN_ID_CONNECT("openIdConnect"),
}