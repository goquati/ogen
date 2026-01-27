package de.quati.ogen.plugin.intern.model

import de.quati.ogen.plugin.intern.codegen.ComponentsContext


internal data class Spec(
    val version: Version,
    val paths: Endpoints,
    val components: Components,
    val security: Security,
) {
    enum class Version {
        V3_0, V3_1
    }

    data class Endpoints(
        val paths: List<Endpoint>,
    ) {
        val groupedByTag get() = paths.groupBy { it.tag }
    }

    data class Components(
        override val schemas: Map<ComponentName.Schema, Component.Schema>,
        override val parameters: Map<ComponentName.Parameter, Endpoint.Parameter>,
        override val requestBody: Map<ComponentName.RequestBody, Endpoint.RequestBody>,
        override val response: Map<ComponentName.Response, Endpoint.Response>,
    ) : ComponentsContext
}