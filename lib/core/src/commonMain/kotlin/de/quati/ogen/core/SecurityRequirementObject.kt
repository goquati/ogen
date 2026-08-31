package de.quati.ogen.core

public sealed interface SecurityRequirementObject {
    public val name: String

    public data class ApiKey(
        override val name: String,
    ) : SecurityRequirementObject

    public data class Http(
        override val name: String,
    ) : SecurityRequirementObject

    public data class Oauth2(
        override val name: String,
    ) : SecurityRequirementObject

    public data class OpenIdConnect(
        override val name: String,
    ) : SecurityRequirementObject

    public data class MutualTLS(
        override val name: String,
    ) : SecurityRequirementObject
}
