package de.quati.ogen.api

import de.quati.ogen.gen.server.DebugApi
import org.springframework.stereotype.Service

@Service
class DebugController : DebugApi {
    override suspend fun debugInfo(
        op: DebugApi.DebugInfoContext,
        debugSession: String?,
    ) = op.createResponse200 {
        addInputHeader(debugSession)
    }

    override suspend fun debugParameterNames(
        op: DebugApi.DebugParameterNamesContext,
        filterField: String,
        scopeProjectId: String?,
    ) = op.createResponse200 {
        addInputHeader(filterField, scopeProjectId)
    }
}