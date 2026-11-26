package de.quati.ogen.plugin.intern.oas

import de.quati.ogen.plugin.intern.oas.decode.OasDecoder
import de.quati.ogen.plugin.intern.oas.load.Bundle
import de.quati.ogen.plugin.intern.oas.load.Bundler
import de.quati.ogen.plugin.intern.oas.load.LoadedSpec
import de.quati.ogen.plugin.intern.oas.load.OasLoader
import de.quati.ogen.plugin.intern.oas.load.ResourceLoader
import de.quati.ogen.plugin.intern.oas.model.OpenApi
import de.quati.ogen.plugin.intern.oas.ref.JsonPointer
import java.net.URI
import java.nio.file.Path

/**
 * A parsed spec. [openApi] is null if [diagnostics] has an error. [bundle] is the self-contained document it was
 * decoded from, [loaded] the documents as they were read.
 */
internal class OasParseResult(
    val openApi: OpenApi?,
    val sourceVersion: OasVersion?,
    val bundle: Bundle?,
    val loaded: LoadedSpec,
    val diagnostics: List<Diagnostic>,
) {
    val errors: List<Diagnostic> get() = diagnostics.filter { it.severity == Diagnostic.Severity.ERROR }
    val warnings: List<Diagnostic> get() = diagnostics.filter { it.severity == Diagnostic.Severity.WARNING }
}

/**
 * Reads an OpenAPI 3.0, 3.1 or 3.2 spec with everything it refers to: load ([OasLoader], which also migrates to
 * 3.2), bundle into one document ([Bundler]), check and decode ([OasDecoder]).
 */
internal object OasParser {
    fun parse(path: Path, resources: ResourceLoader = ResourceLoader.default()): OasParseResult =
        parse(path.toAbsolutePath().toUri(), resources)

    fun parse(uri: URI, resources: ResourceLoader = ResourceLoader.default()): OasParseResult {
        val loaded = OasLoader(resources).load(uri)
        if (loaded.root == null || loaded.sourceVersion == null)
            return OasParseResult(null, loaded.sourceVersion, null, loaded, loaded.diagnostics)
        val bundle = Bundler(loaded).bundle()
        val decoded = OasDecoder.decode(bundle.element, OpenApi.serializer(), JsonPointer.ROOT, bundle::locate)
        val diagnostics = loaded.diagnostics + bundle.diagnostics + decoded.diagnostics
        val failed = diagnostics.any { it.severity == Diagnostic.Severity.ERROR }
        return OasParseResult(decoded.value.takeUnless { failed }, loaded.sourceVersion, bundle, loaded, diagnostics)
    }
}
