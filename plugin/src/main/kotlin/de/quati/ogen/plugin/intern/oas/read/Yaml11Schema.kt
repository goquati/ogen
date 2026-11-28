package de.quati.ogen.plugin.intern.oas.read

import org.snakeyaml.engine.v2.api.ConstructNode
import org.snakeyaml.engine.v2.nodes.Tag
import org.snakeyaml.engine.v2.resolver.ScalarResolver
import org.snakeyaml.engine.v2.schema.Schema
import java.math.BigInteger
import java.util.regex.Pattern

/**
 * How swagger-parser resolved plain scalars, so a spec reads as it did before ogen had its own parser: the YAML 1.1
 * patterns of snakeyaml (swagger-parser's `DeserializationUtils.CustomResolver`, without timestamps). Unquoted
 * `yes`, `no`, `on` and `off` are booleans, `010` is octal, `1_000` and `1:30` are numbers.
 */
internal object Yaml11Schema : Schema {
    // snakeyaml 2.6, org.yaml.snakeyaml.resolver.Resolver
    private val BOOL = Pattern.compile("^(?:yes|Yes|YES|no|No|NO|true|True|TRUE|false|False|FALSE|on|On|ON|off|Off|OFF)$")
    private val INT = Pattern.compile(
        "^(?:[-+]?0b_*[0-1][0-1_]*|[-+]?0_*[0-7][0-7_]*|[-+]?(?:0|[1-9][0-9_]*)|[-+]?0x_*[0-9a-fA-F][0-9a-fA-F_]*" +
            "|[-+]?[1-9][0-9_]*(?::[0-5]?[0-9])+)$"
    )
    private val FLOAT = Pattern.compile(
        "^([-+]?(?:[0-9][0-9_]*)\\.[0-9_]*(?:[eE][-+]?[0-9]+)?|[-+]?(?:[0-9][0-9_]*)(?:[eE][-+]?[0-9]+)" +
            "|[-+]?\\.[0-9_]+(?:[eE][-+]?[0-9]+)?|[-+]?[0-9][0-9_]*(?::[0-5]?[0-9])+\\.[0-9_]*" +
            "|[-+]?\\.(?:inf|Inf|INF)|\\.(?:nan|NaN|NAN))$"
    )
    private val MERGE = Pattern.compile("^(?:<<)$")
    private val NULL = Pattern.compile("^(?:~|null|Null|NULL| )$")
    private val EMPTY = Pattern.compile("^$")

    // In the order of swagger-parser's CustomResolver; the first that matches wins.
    private val resolvers = listOf(Tag.BOOL to BOOL, Tag.INT to INT, Tag.FLOAT to FLOAT, Tag.MERGE to MERGE, Tag.NULL to NULL, Tag.NULL to EMPTY)

    private val resolver = ScalarResolver { value, implicit ->
        if (implicit) resolvers.firstOrNull { (_, pattern) -> pattern.matcher(value).matches() }?.first ?: Tag.STR
        else Tag.STR
    }

    override fun getScalarResolver(): ScalarResolver = resolver
    override fun getSchemaTagConstructors(): Map<Tag, ConstructNode> = emptyMap()

    // The YAML 1.2 core schema: what it reads as null, a boolean or a number.
    private val CORE_NON_STRING = Regex(
        "null|Null|NULL|~|true|True|TRUE|false|False|FALSE|[-+]?[0-9]+|0o[0-7]+|0x[0-9a-fA-F]+" +
            "|[-+]?(\\.[0-9]+|[0-9]+(\\.[0-9]*)?)([eE][-+]?[0-9]+)?|[-+]?\\.(inf|Inf|INF)|\\.(nan|NaN|NAN)"
    )

    /** Whether YAML 1.2's core schema, too, reads the plain scalar [value] as null, a boolean or a number. */
    fun isYaml12NonString(value: String): Boolean = value.isEmpty() || CORE_NON_STRING.matches(value)

    fun toBoolean(value: String): Boolean = value.lowercase() in setOf("yes", "true", "on")

    /** An int as snakeyaml's ConstructYamlInt reads it: signs, `_`, `0b`, `0x`, a leading `0` for octal, `:` for base 60. */
    fun toInteger(text: String): BigInteger {
        var value = text.replace("_", "")
        val negative = value.startsWith("-")
        if (value.startsWith("-") || value.startsWith("+")) value = value.substring(1)
        val number = when {
            value == "0" -> BigInteger.ZERO
            value.startsWith("0b") -> BigInteger(value.substring(2), 2)
            value.startsWith("0x") -> BigInteger(value.substring(2), 16)
            value.startsWith("0") -> BigInteger(value.substring(1), 8)
            ':' in value -> value.split(':').fold(BigInteger.ZERO) { acc, digit -> acc * BigInteger.valueOf(60) + BigInteger(digit) }
            else -> BigInteger(value)
        }
        return if (negative) number.negate() else number
    }

    /** A float as snakeyaml's ConstructYamlFloat reads it, a double; null for infinity and NaN, which JSON lacks. */
    fun toDouble(text: String): Double? {
        var value = text.replace("_", "")
        val sign = if (value.startsWith("-")) -1 else 1
        if (value.startsWith("-") || value.startsWith("+")) value = value.substring(1)
        val number = when {
            value.lowercase() == ".inf" || value.lowercase() == ".nan" -> return null
            ':' in value -> value.split(':').fold(0.0) { acc, digit -> acc * 60 + digit.toDouble() }
            else -> value.toDouble()
        }
        return number * sign
    }
}
