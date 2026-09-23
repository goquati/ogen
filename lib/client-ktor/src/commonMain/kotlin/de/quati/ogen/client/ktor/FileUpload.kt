package de.quati.ogen.client.ktor

public class FileUpload(
    public val fileName: String,
    public val content: ByteArray,
    public val contentType: String? = null,
)
