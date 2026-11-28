package de.quati.ogen.api

import de.quati.ogen.gen.model.DebugLabelDto
import de.quati.ogen.gen.model.DebugNoteDto
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

    override suspend fun debugNullableParameters(
        op: DebugApi.DebugNullableParametersContext,
        id: DebugTraceIdDto,
        traceId: DebugTraceIdDto?,
        note: String?,
    ) = op.createResponse200 {
        addInputHeader(id, traceId, note)
    }

    override suspend fun debugStringParameters(
        op: DebugApi.DebugStringParametersContext,
        label: DebugLabelDto,
        tags: List<String>,
    ) = op.createResponse200 {
        addInputHeader(label, tags.joinToString(","))
    }

    override suspend fun debugOptionalBinaryBody(
        op: DebugApi.DebugOptionalBinaryBodyContext,
        body: Any?,
    ) = op.createResponse200 {
        addInputHeader(body?.let { it::class.simpleName })
    }

    override suspend fun debugOptionalJsonBody(
        op: DebugApi.DebugOptionalJsonBodyContext,
        debugNoteDto: DebugNoteDto?,
    ) = op.createResponse200 {
        addInputHeader(debugNoteDto?.text)
    }
}
