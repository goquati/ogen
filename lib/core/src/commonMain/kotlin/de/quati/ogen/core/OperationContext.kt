package de.quati.ogen.core

public interface OperationContext {
    public val name: String

    public val description: String?

    public val deprecated: Boolean

    public val tag: String

    public val security: List<SecurityRequirement>

    public val requestBody: Body?

    public val responses: Map<String, Body>

    public val defaultSuccessStatus: Int

    public data class Body(
        public val description: String?,
        public val contentTypes: Set<String>,
    )
}
