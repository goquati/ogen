package de.quati.ogen.api

import de.quati.ogen.gen.model.DebugLabelDto
import de.quati.ogen.gen.model.DebugTraceIdDto
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

    override suspend fun debugNullableRefParameter(
        op: DebugApi.DebugNullableRefParameterContext,
        traceId: DebugTraceIdDto,
    ) = op.createResponse200 {
        addInputHeader(traceId)
    }

    override suspend fun debugStringParameters(
        op: DebugApi.DebugStringParametersContext,
        label: DebugLabelDto,
        tags: List<String>,
    ) = op.createResponse200 {
        addInputHeader(label, tags.joinToString(","))
    }
}