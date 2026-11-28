package de.quati.ogen.api

import de.quati.ogen.gen.model.DebugLabelDto
import de.quati.ogen.gen.model.DebugNoteDto
import de.quati.ogen.gen.model.DebugTraceIdDto
import de.quati.ogen.gen.server.DebugApi
import org.springframework.http.codec.multipart.FilePart
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
        body: ByteArray?,
    ) = op.createResponse200 {
        addInputHeader(body?.size)
    }

    override suspend fun debugTextBody(
        op: DebugApi.DebugTextBodyContext,
        body: String?,
    ) = op.createResponse200 {
        addInputHeader(body)
    }

    override suspend fun debugJsonStringBody(
        op: DebugApi.DebugJsonStringBodyContext,
        debugLabelDto: DebugLabelDto,
    ) = op.createResponse200 {
        addInputHeader(debugLabelDto.value)
    }

    override suspend fun debugJsonInlineStringBody(
        op: DebugApi.DebugJsonInlineStringBodyContext,
        string: String?,
    ) = op.createResponse200 {
        addInputHeader(string)
    }

    override suspend fun debugOptionalJsonBody(
        op: DebugApi.DebugOptionalJsonBodyContext,
        debugNoteDto: DebugNoteDto?,
    ) = op.createResponse200 {
        addInputHeader(debugNoteDto?.text)
    }

    override suspend fun debugJsonCharsetBody(
        op: DebugApi.DebugJsonCharsetBodyContext,
        debugNoteDto: DebugNoteDto,
    ) = op.createResponse200 {
        addInputHeader(debugNoteDto.text)
    }

    override suspend fun debugOptionalMultipartBody(
        op: DebugApi.DebugOptionalMultipartBodyContext,
        `file`: FilePart?,
        note: String?,
    ) = op.createResponse200 {
        addInputHeader(file?.filename(), note)
    }
}
