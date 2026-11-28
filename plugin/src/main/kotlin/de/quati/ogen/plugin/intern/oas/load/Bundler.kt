package de.quati.ogen.plugin.intern.oas.load

import de.quati.ogen.plugin.intern.oas.Diagnostic
import de.quati.ogen.plugin.intern.oas.SourceLocation
import de.quati.ogen.plugin.intern.oas.ref.JsonPointer
import de.quati.ogen.plugin.intern.oas.ref.RefTarget
import de.quati.ogen.plugin.intern.oas.ref.at
import de.quati.ogen.plugin.intern.oas.ref.replace
import de.quati.ogen.plugin.intern.oas.serial.OasSerialNames
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI

/**
 * One self-contained document: everything of other files that the spec refers to is in `components`, and all
 * references are local (`#/components/schemas/User`). [locate] finds the original file and position of any
 * place in it.
 */
internal class Bundle(
    val element: JsonObject,
    private val root: Document,
    private val documents: Map<URI, Document>,
    /** Where the parts of other files that were moved into [element] came from, by their place in [element]. */
    private val origins: Map<JsonPointer, Located>,
    val diagnostics: List<Diagnostic>,
) {
    fun locate(pointer: JsonPointer, key: Boolean = false): SourceLocation {
        val (document, source) = origin(pointer)
        // Positions come from the original file; the pointer names the place in the bundle.
        return document.locationOf(source, key).copy(pointer = pointer)
    }

    /** The document a place in the bundle came from, and the place there. */
    fun origin(pointer: JsonPointer): Pair<Document, JsonPointer> {
        val (moved, origin) = origins.entries
            .filter { (at, _) -> pointer.segments.take(at.segments.size) == at.segments }
            .maxByOrNull { it.key.segments.size }
            ?.toPair() ?: return root to pointer
        val inner = JsonPointer.of(*pointer.segments.drop(moved.segments.size).toTypedArray())
        val document = documents[origin.document] ?: return root to pointer
        return document to origin.pointer + inner
    }

    /** What a place in the bundle was in its file, as written: before references were bundled and before migration. */
    fun source(pointer: JsonPointer): JsonElement? = origin(pointer).let { (document, at) -> document.read.element?.at(at) }

    /** The text of the plain scalar at [pointer] if YAML 1.1, as the reader has it, reads it otherwise than YAML 1.2. */
    fun yaml11Text(pointer: JsonPointer): String? = origin(pointer).let { (document, at) -> document.read.yaml11Scalars[at] }
}

/**
 * Bundles a [LoadedSpec] the way swagger-parser resolved external references, so components keep their names:
 *
 * - A target in another file moves to `components/<kind>/<name>`, named by the last segment of its pointer, or by
 *   the file name without extension if the reference names the whole file. On a name conflict (for schemas
 *   regardless of case) `_1`, `_2`, … is appended, unless the existing component has the same content.
 * - A root component that only refers to that target (`User: {$ref: 'common.yaml#/…/User'}`) is replaced by it.
 * - Path Item references are replaced by the Path Item itself, merged with the fields written next to the `$ref`.
 * - References inside moved parts are bundled the same way; discriminator mappings are references, too.
 * - Paths are bundled before components, as swagger-parser processes them; see [localRef] for the order of imports.
 */
internal class Bundler(private val spec: LoadedSpec) {
    private val root = spec.root ?: error("the spec has no root document")
    private val components = LinkedHashMap<String, LinkedHashMap<String, JsonElement>>()
    private val imported = mutableMapOf<Pair<Located, String>, String>()
    private val origins = mutableMapOf<JsonPointer, Located>()
    private val inlining = mutableSetOf<Located>()
    private val diagnostics = mutableListOf<Diagnostic>()
    private val sitesByDocument = spec.refs.filter { it.target != null }.groupBy { it.document }

    fun bundle(): Bundle {
        val rootElement = root.element as? JsonObject ?: error("the root document is not an object")
        (rootElement["components"] as? JsonObject)?.forEach { (kind, entries) ->
            (entries as? JsonObject)?.let { components.getOrPut(kind) { LinkedHashMap() }.putAll(it) }
        }
        // Paths first, then components, as swagger-parser processes them; the order decides names on conflicts.
        val rootSites = sitesByDocument[spec.rootUri].orEmpty().sortedBy {
            when (it.pointer.segments.firstOrNull()) {
                "paths" -> 0
                "components" -> 1
                else -> 2
            }
        }
        val rewritten = rootElement.replace(
            rootSites.mapNotNull { replacement(it, JsonPointer.ROOT, from = null, bundledAt = JsonPointer.ROOT) }.toMap()
        ) as JsonObject
        // Deferred imports, in the order they were found; each may defer more.
        while (queue.isNotEmpty()) {
            val (kind, name, located) = queue.removeFirst()
            import(kind, name, deferred.getValue(kind).remove(name)!!, located)
        }
        // A component keeps its rewritten root value, unless something of another file was imported under its name.
        val rewrittenComponents = rewritten["components"] as? JsonObject
        val bundledComponents = LinkedHashMap<String, JsonElement>(rewrittenComponents ?: emptyMap())
        components.forEach { (kind, entries) ->
            val fromRoot = rewrittenComponents?.get(kind) as? JsonObject
            bundledComponents[kind] = JsonObject(entries.mapValues { (name, imported) ->
                if (name in importedNames[kind].orEmpty()) imported else fromRoot?.get(name) ?: imported
            })
        }
        val element = if (bundledComponents.isEmpty()) rewritten
        else JsonObject(rewritten + ("components" to JsonObject(bundledComponents)))
        return Bundle(element, root, spec.documents, origins, diagnostics)
    }

    private val importedNames = mutableMapOf<String, MutableSet<String>>()
    private val queue = ArrayDeque<Triple<String, String, Located>>()
    // Imports whose name is taken but which come into the components later.
    private val deferred = mutableMapOf<String, LinkedHashMap<String, JsonElement>>()

    /**
     * The new value at the place of [site], relative to [base] (the start of the part being rewritten);
     * [from] is the document of that part, null for the root document, and [bundledAt] where the part lies in
     * the bundle.
     */
    private fun replacement(site: RefSite, base: JsonPointer, from: URI?, bundledAt: JsonPointer): Pair<JsonPointer, JsonElement>? {
        val relative = site.pointer.relativeTo(base) ?: return null
        val target = site.target ?: return null
        if (kindOf(site.expected) == PATH_ITEM) {
            val item = relative.parent!!
            val inlined = inlinePathItem(target, bundledAt + item) ?: return null
            return item to mergeSiblings(site, inlined, from, bundledAt + item)
        }
        return relative to JsonPrimitive(localRef(target, site.expected, from))
    }

    /**
     * The local reference to [target]; a target in another file is imported into the components. As in
     * swagger-parser, which decides the order of the components, a reference from the root document or into the
     * file of the part it stands in is imported at once, one into a third file later.
     */
    private fun localRef(target: RefTarget, expected: SerialDescriptor, from: URI?): String {
        val located = spec.locate(target) ?: return target.toString()
        if (located.document == spec.rootUri) return "#${located.pointer.toUriFragment()}"
        val kind = kindOf(expected) ?: return target.toString()
        imported[located to kind]?.let { return "#/components/$kind/${JsonPointer.of(it).toUriFragment().removePrefix("/")}" }
        val element = spec.documents[located.document]?.element?.at(located.pointer) ?: return target.toString()
        val entries = components.getOrPut(kind) { LinkedHashMap() }
        val later = deferred.getOrPut(kind) { LinkedHashMap() }
        val name = allocate(kind, entries + later, baseName(target), target)
        imported[located to kind] = name
        importedNames.getOrPut(kind) { mutableSetOf() } += name
        origins[JsonPointer.of("components", kind, name)] = located
        if (from == null || from == located.document) import(kind, name, element, located)
        else {
            later[name] = element
            queue += Triple(kind, name, located)
        }
        return "#/components/$kind/${JsonPointer.of(name).toUriFragment().removePrefix("/")}"
    }

    private fun import(kind: String, name: String, element: JsonElement, located: Located) {
        val entries = components.getValue(kind)
        // The name is in the components before the part is rewritten, so references back to it find it.
        entries[name] = element
        entries[name] = rewrite(element, located, JsonPointer.of("components", kind, name))
    }

    /** [element], a part of another document found at [located], with its references bundled; it goes to [bundledAt]. */
    private fun rewrite(element: JsonElement, located: Located, bundledAt: JsonPointer): JsonElement {
        val replacements = sitesByDocument[located.document].orEmpty()
            .mapNotNull { site -> replacement(site, located.pointer, from = located.document, bundledAt = bundledAt) }
        return element.replace(replacements.toMap())
    }

    private fun inlinePathItem(target: RefTarget, bundledAt: JsonPointer): JsonElement? {
        val located = spec.locate(target) ?: return null
        val element = spec.documents[located.document]?.element?.at(located.pointer) ?: return null
        if (!inlining.add(located)) {
            diagnostics += Diagnostic.error("Path Item references form a cycle at '$target'", SourceLocation(target.toString()))
            return null
        }
        if (located.document != spec.rootUri || located.pointer != bundledAt) origins[bundledAt] = located
        try {
            return rewrite(element, located, bundledAt)
        } finally {
            inlining -= located
        }
    }

    /**
     * [inlined] with the fields written next to the Path Item `$ref` of [site]; their references are bundled, too.
     * OpenAPI leaves a field in both undefined; the one next to the `$ref` wins, with a warning if the values differ.
     */
    private fun mergeSiblings(site: RefSite, inlined: JsonElement, from: URI?, bundledAt: JsonPointer): JsonElement {
        val itemPointer = site.pointer.parent!!
        val document = spec.documents[site.document] ?: return inlined
        val item = document.element.at(itemPointer) as? JsonObject ?: return inlined
        val siblings = JsonObject(item - OasSerialNames.REF_KEY)
        if (siblings.isEmpty() || inlined !is JsonObject) return inlined
        val rewritten = siblings.replace(
            sitesByDocument[site.document].orEmpty()
                .filter { it !== site && it.pointer.relativeTo(itemPointer) != null }
                .mapNotNull { replacement(it, itemPointer, from, bundledAt) }
                .toMap()
        ) as JsonObject
        rewritten.keys.forEach { key ->
            // The field comes from the file of the `$ref`, not from the file of the inlined Path Item.
            origins[bundledAt / key] = Located(site.document, itemPointer / key)
            if (key in inlined && inlined[key] != rewritten[key]) diagnostics += Diagnostic.warning(
                "'$key' is both next to the Path Item \$ref and in the referenced Path Item; the one next to the \$ref is used",
                document.locationOf(itemPointer / key, key = true).copy(pointer = bundledAt / key),
            )
        }
        return JsonObject(inlined + rewritten)
    }

    private fun allocate(kind: String, entries: Map<String, JsonElement>, baseName: String, target: RefTarget): String {
        val ignoreCase = kind == "schemas"
        for (suffix in 0..Int.MAX_VALUE) {
            val candidate = if (suffix == 0) baseName else "${baseName}_$suffix"
            val existingKey = entries.keys.firstOrNull { it.equals(candidate, ignoreCase = ignoreCase) } ?: return candidate
            val existing = entries.getValue(existingKey)
            val refersToTarget = (existing as? JsonObject)?.get(OasSerialNames.REF_KEY)
                ?.let { (it as? JsonPrimitive)?.content }
                ?.let { runCatching { RefTarget.parse(it, spec.rootUri) }.getOrNull() } == target
            val sameContent = spec.locate(target)?.let { spec.documents[it.document]?.element?.at(it.pointer) } == existing
            if (refersToTarget || sameContent) return existingKey
        }
        error("unreachable")
    }

    // The last segment of the pointer; for a whole file its name without extension, as swagger-parser names it.
    private fun baseName(target: RefTarget): String {
        target.pointer.segments.lastOrNull()?.let { return it }
        val file = target.document.path.substringAfterLast('/')
        val parts = file.split('.')
        return if (parts.size > 2) parts.dropLast(1).joinToString("") else parts.first()
    }


    private companion object {
        const val PATH_ITEM = "pathItems"

        fun kindOf(descriptor: SerialDescriptor): String? {
            val name = descriptor.serialName.removeSuffix("?").substringAfterLast('.')
            return when (name) {
                "Schema" -> "schemas"
                "Response" -> "responses"
                "Parameter" -> "parameters"
                "Example" -> "examples"
                "RequestBody" -> "requestBodies"
                "Header" -> "headers"
                "SecurityScheme" -> "securitySchemes"
                "Link" -> "links"
                "MediaType" -> "mediaTypes"
                "PathItem" -> PATH_ITEM
                else -> if (descriptor.serialName.startsWith("${OasSerialNames.EXTENSIBLE_MAP}<Callback>")) "callbacks" else null
            }
        }
    }
}
