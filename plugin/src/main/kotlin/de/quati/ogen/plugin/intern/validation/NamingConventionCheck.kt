package de.quati.ogen.plugin.intern.validation

import de.quati.ogen.plugin.NameConvention
import de.quati.ogen.plugin.intern.model.config.ValidatorConfig
import de.quati.ogen.plugin.intern.oas.model.Parameter
import kotlinx.serialization.json.JsonPrimitive

/**
 * Names that don't match the validator's naming conventions: path segments, tags and operationIds of every operation,
 * every parameter, component schema names, and the property names and string enum values of every schema.
 */
internal fun namingConventionErrors(spec: ValidatedSpec, config: ValidatorConfig): List<String> {
    val errors = mutableListOf<String>()
    fun check(kind: String, name: String, location: String, format: NameConvention) {
        if (!format.matches(name)) errors.add("$kind '$name' in '$location' does not match the $kind format $format")
    }

    // A variable stands for a value that fits the convention: lower-case where it starts a camelCase or kebab-case
    // name, upper-case where it starts a PascalCase one, either within a name.
    fun checkPathPart(kind: String, part: String, location: String, format: NameConvention) {
        // A part that is only variables (`{id}`, `{id}{rev}`) has no text of its own.
        if (part.replace(pathVariable, "").isEmpty()) return
        // As written first, so an exception can name the part with its variables.
        if (format.matches(part) || variablePlaceholders.any { format.matches(part.replace(pathVariable, it)) }) return
        errors.add("$kind '$part' in '$location' does not match the $kind format $format")
    }

    spec.api.paths?.pathItems.orEmpty().keys.forEach { path ->
        // The root path `/`, a trailing slash and a double slash leave an empty segment, which is no name.
        path.trimStart('/').split("/").filter { it.isNotEmpty() }.forEach { segment ->
            val (names, extensions) = pathSegmentParts(segment)
            names.forEach { checkPathPart("path segment", it, path, config.naming.pathSegment) }
            extensions.forEach { checkPathPart("path extension", it, path, config.naming.pathExtension) }
        }
    }

    spec.operations().forEach { node ->
        node.operation.tags.orEmpty().forEach { check("tag", it, node.location, config.naming.tag) }
        // A blank operationId counts as none.
        node.operation.operationId?.takeIf { it.isNotBlank() }?.let { check("operationId", it, node.location, config.naming.operationId) }
    }

    spec.parameters().forEach { node ->
        val parameter = node.parameter
        // A parameter without name or location is reported by the parser.
        val name = parameter.name ?: return@forEach
        val (placement, format) = when (parameter.location ?: return@forEach) {
            Parameter.Location.PATH -> "path" to config.naming.parameter.path
            Parameter.Location.QUERY -> "query" to config.naming.parameter.query
            // The whole query string as one parameter (3.2) is named like a query parameter.
            Parameter.Location.QUERYSTRING -> "querystring" to config.naming.parameter.query
            Parameter.Location.HEADER -> "header" to config.naming.parameter.header
            Parameter.Location.COOKIE -> "cookie" to config.naming.parameter.cookie
        }
        check("$placement parameter", name, node.ownerLocation, format)
    }

    spec.api.components?.schemas.orEmpty().keys.forEach {
        check("schema name", it, "components.schemas", config.naming.schema)
    }
    spec.walkSchemas { node ->
        node.schema.properties.orEmpty().keys
            .forEach { check("property", it, node.location, config.naming.property) }
        // A naming convention can only apply to a string value.
        node.schema.enum.orEmpty().filterIsInstance<JsonPrimitive>().filter { it.isString }
            .forEach { check("enum value", it.content, node.location, config.naming.enumValue) }
    }
    return errors
}

// Letters only, so a convention without digits accepts them; two of them, so a convention without acronyms reads
// one word (`XxReport`, `byxxName`).
private val variablePlaceholders = listOf("xx", "Xx")

/**
 * The names and the extensions of a path segment, with its variables as written. The segment splits at `:`
 * (`{id}:cancel`); in each piece, leading dots (`.well-known`) are dropped, the text up to the next `.` is a name and
 * the parts after it are extensions (`archive.tar.gz`). A `.` or `:` inside a variable splits nothing; empty parts are
 * left out.
 */
internal fun pathSegmentParts(segment: String): Pair<List<String>, List<String>> {
    val names = mutableListOf<String>()
    val extensions = mutableListOf<String>()
    segment.splitOutsideVariables(':').forEach { piece ->
        val parts = piece.trimStart('.').splitOutsideVariables('.')
        names += parts.first()
        extensions += parts.drop(1)
    }
    return names.filter { it.isNotEmpty() } to extensions.filter { it.isNotEmpty() }
}

private fun String.splitOutsideVariables(separator: Char): List<String> {
    val variables = pathVariable.findAll(this).map { it.range }.toList()
    val parts = mutableListOf<String>()
    var start = 0
    forEachIndexed { index, char ->
        if (char == separator && variables.none { index in it }) {
            parts += substring(start, index)
            start = index + 1
        }
    }
    return parts + substring(start)
}
