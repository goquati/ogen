package de.quati.ogen.plugin.intern.validation

import io.swagger.v3.oas.models.OpenAPI
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * `pattern` values and `patternProperties` keys that don't compile. OpenAPI patterns are ECMA-262 regexes, which the
 * JVM can only approximate: Java rejects some valid ones (`[^]`, `\u{41}`, a literal `{`). So these are warnings.
 */
internal fun patternWarnings(spec: OpenAPI): List<String> {
    val warnings = mutableListOf<String>()
    fun check(kind: String, regex: String, location: String) {
        try {
            Pattern.compile(regex)
        } catch (e: PatternSyntaxException) {
            warnings.add("$kind '$regex' in '$location' does not compile as a Java regex: ${e.description}")
        }
    }
    spec.walkSchemas { node ->
        node.schema.pattern?.let { check("pattern", it, node.location) }
        node.schema.patternProperties?.keys?.forEach { check("patternProperties key", it, node.location) }
    }
    return warnings
}
