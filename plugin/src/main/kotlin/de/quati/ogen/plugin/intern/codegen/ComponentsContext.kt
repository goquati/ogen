package de.quati.ogen.plugin.intern.codegen

import de.quati.ogen.plugin.intern.model.Component
import de.quati.ogen.plugin.intern.model.ComponentName
import de.quati.ogen.plugin.intern.model.Endpoint

internal interface ComponentsContext {
    val schemas: Map<ComponentName.Schema, Component.Schema>
    val parameters: Map<ComponentName.Parameter, Endpoint.Parameter>
    val requestBody: Map<ComponentName.RequestBody, Endpoint.RequestBody>
    val response: Map<ComponentName.Response, Endpoint.Response>
}