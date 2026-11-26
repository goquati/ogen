package de.quati.ogen.plugin.intern.oas.load

import de.quati.ogen.plugin.intern.oas.Diagnostic
import de.quati.ogen.plugin.intern.oas.SourceLocation
import de.quati.ogen.plugin.intern.oas.model.OpenApi
import de.quati.ogen.plugin.intern.oas.read.ReadResult
import de.quati.ogen.plugin.intern.oas.read.SourceReader
import de.quati.ogen.plugin.intern.oas.ref.JsonPointer
import de.quati.ogen.plugin.intern.oas.ref.RefTarget
import de.quati.ogen.plugin.intern.oas.ref.at
import de.quati.ogen.plugin.intern.oas.ref.canonical
import de.quati.ogen.plugin.intern.oas.ref.replace
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI

/** A place in a loaded document. */
internal data class Located(val document: URI, val pointer: JsonPointer)

/**
 * A reference found while loading: in [document] at [pointer] (the reference string itself) stands [ref], which
 * must point to something of [expected]. [target] is null if [ref] is not a valid reference.
 */
internal class RefSite(
    val document: URI,
    val pointer: JsonPointer,
    val ref: String,
    val target: RefTarget?,
    val expected: SerialDescriptor,
)

/**
 * A loaded document. In [element], every reference the loader found is absolute; [read] keeps the positions.
 * [base] is the URI references in it are relative to: its `$self`, or where it was loaded from.
 */
internal class Document(val uri: URI, val base: URI, val read: ReadResult, val element: JsonElement) {
    fun locationOf(pointer: JsonPointer, key: Boolean = false): SourceLocation = read.locationOf(pointer, key)
}

/** The root document and everything its references reach. */
internal class LoadedSpec(
    val rootUri: URI,
    val documents: Map<URI, Document>,
    /** Schemas with `$id`, by that URI. */
    val schemaResources: Map<URI, Located>,
    val refs: List<RefSite>,
    val diagnostics: List<Diagnostic>,
) {
    val root: Document? get() = documents[rootUri]

    /** Where an absolute reference points to, if that document is loaded. */
    fun locate(target: RefTarget): Located? =
        schemaResources[target.document]?.let { Located(it.document, it.pointer + target.pointer) }
            ?: target.takeIf { it.document in documents }?.let { Located(it.document, it.pointer) }
}

/**
 * Loads a spec and every document its references reach, and makes all references absolute.
 *
 * Starting at the root document, it walks the tree along the model ([OasWalker]); each reference is resolved
 * against the base URI where it stands (`$self` of the document, `$id` of enclosing schemas). A referenced
 * document is loaded once, by its absolute URI, and only the referenced part of it is walked, as the type
 * the reference expects. Nothing is dereferenced: recursive schemas stay references.
 */
internal class OasLoader(private val resources: ResourceLoader = ResourceLoader.default()) {
    private class Raw(val uri: URI, val base: URI, val read: ReadResult)
    private data class Task(val document: URI, val pointer: JsonPointer, val expected: SerialDescriptor, val base: URI)

    private val raws = LinkedHashMap<URI, Raw>()
    private val failures = mutableMapOf<URI, String>()
    private val schemaResources = mutableMapOf<URI, Located>()
    private val visited = mutableSetOf<Triple<URI, JsonPointer, String>>()
    private val queue = ArrayDeque<Task>()
    private val sites = mutableListOf<RefSite>()
    private val pending = mutableListOf<RefSite>()
    private val diagnostics = mutableListOf<Diagnostic>()

    // A document with `$self` is identified by that URI, but its neighbours are still loaded from where it lies.
    private val retrievalPrefixes = mutableListOf<Pair<String, String>>()

    fun load(root: URI): LoadedSpec {
        val rootUri = root.canonical()
        val rootRaw = fetch(rootUri)
        if (rootRaw == null) {
            diagnostics += Diagnostic.error("cannot load the spec: ${failures[rootUri]}", SourceLocation(rootUri.toString()))
        } else {
            enqueue(Task(rootUri, JsonPointer.ROOT, OpenApi.serializer().descriptor, rootRaw.base))
            run()
        }
        val replacements = sites.filter { it.target != null }.groupBy { it.document }
        val documents = raws.values.distinct().filter { it.read.element != null }.associateWith { raw ->
            val absolute = replacements[raw.uri].orEmpty().associate { it.pointer to JsonPrimitive(it.target.toString()) }
            Document(raw.uri, raw.base, raw.read, raw.read.element!!.replace(absolute))
        }
        return LoadedSpec(
            rootUri = rootUri,
            // A document with `$self` is found by that URI and by the one it was loaded from.
            documents = raws.entries.mapNotNull { (uri, raw) -> documents[raw]?.let { uri to it } }.toMap(),
            schemaResources = schemaResources.toMap(),
            refs = sites.toList(),
            diagnostics = diagnostics.toList(),
        )
    }

    private fun run() {
        while (true) {
            while (queue.isNotEmpty()) walk(queue.removeFirst())
            // References to schema resources may only be known after other parts are walked; load the rest after.
            val located = pending.filter { site -> locate(site.target!!)?.also { follow(site, it) } != null }
            pending -= located.toSet()
            if (located.isNotEmpty()) continue
            val loaded = pending.map { it.target!!.document }.distinct().filter { fetch(it) != null }
            if (loaded.isEmpty()) break
        }
        pending.forEach { site -> siteError(site, failures[site.target!!.document] ?: "the document was not loaded") }
    }

    private fun walk(task: Task) {
        val raw = raws.getValue(task.document)
        val element = raw.read.element?.at(task.pointer) ?: return
        object : OasWalker() {
            override fun onRef(pointer: JsonPointer, ref: String, expected: SerialDescriptor, base: URI) {
                val target = try {
                    RefTarget.parse(ref, base)
                } catch (e: IllegalArgumentException) {
                    val site = RefSite(task.document, pointer, ref, null, expected)
                    sites += site
                    return siteError(site, e.message ?: "invalid reference")
                }
                val site = RefSite(task.document, pointer, ref, target, expected)
                sites += site
                locate(target)?.let { follow(site, it) } ?: run { pending += site }
            }

            override fun onSchemaResource(pointer: JsonPointer, uri: URI) {
                schemaResources.putIfAbsent(uri, Located(task.document, pointer))
            }
        }.walk(element, task.expected, task.pointer, task.base)
    }

    private fun locate(target: RefTarget): Located? =
        schemaResources[target.document]?.let { Located(it.document, it.pointer + target.pointer) }
            ?: target.takeIf { raws[it.document]?.read?.element != null }?.let { Located(it.document, it.pointer) }

    private fun follow(site: RefSite, located: Located) {
        val raw = raws.getValue(located.document)
        if (raw.read.element?.at(located.pointer) == null)
            return siteError(site, "'${located.pointer}' does not exist in ${raw.read.source}")
        enqueue(Task(located.document, located.pointer, site.expected, raw.base))
    }

    private fun enqueue(task: Task) {
        if (visited.add(Triple(task.document, task.pointer, task.expected.serialName.removeSuffix("?")))) queue += task
    }

    /** The document at [uri], loaded once; null if it cannot be loaded or read. */
    private fun fetch(uri: URI): Raw? {
        raws[uri]?.let { return it.takeIf { raw -> raw.read.element != null } }
        if (uri in failures) return null
        val resource = resources.load(retrievalUri(uri))
        val read = when (resource) {
            is ResourceLoader.Resource.Failure -> {
                failures[uri] = resource.reason
                return null
            }
            is ResourceLoader.Resource.Text -> SourceReader.read(resource.text, resource.source)
        }
        diagnostics += read.diagnostics
        val base = selfBase(uri, read)
        val raw = Raw(uri, base, read)
        raws[uri] = raw
        if (base != uri) {
            raws.putIfAbsent(base, raw)
            retrievalPrefixes += base.resolve(".").toString() to retrievalUri(uri).resolve(".").toString()
        }
        if (read.element == null) failures[uri] = "${read.source} could not be read"
        return raw.takeIf { read.element != null }
    }

    private fun retrievalUri(uri: URI): URI {
        val text = uri.toString()
        val (from, to) = retrievalPrefixes.filter { text.startsWith(it.first) }.maxByOrNull { it.first.length } ?: return uri
        return URI(to + text.removePrefix(from))
    }

    // `$self` (3.2) at the root of a document sets its base URI.
    private fun selfBase(uri: URI, read: ReadResult): URI {
        val self = ((read.element as? JsonObject)?.get(SELF_KEY) as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: return uri
        return runCatching { uri.resolve(URI(self)).canonical() }.getOrElse {
            diagnostics += Diagnostic.error("'\$self' is not a valid URI: $self", read.locationOf(JsonPointer.of(SELF_KEY)))
            uri
        }
    }

    private fun siteError(site: RefSite, reason: String) {
        val location = raws[site.document]?.read?.locationOf(site.pointer, key = true) ?: SourceLocation(site.document.toString())
        diagnostics += Diagnostic.error("cannot resolve '${site.ref}': $reason", location)
    }

    private companion object {
        const val SELF_KEY = $$"$self"
    }
}
