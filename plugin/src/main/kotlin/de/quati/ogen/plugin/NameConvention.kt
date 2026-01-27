package de.quati.ogen.plugin

public sealed interface NameConvention {
    public fun matches(value: String): Boolean

    public object Any : NameConvention {
        override fun toString(): String = "any"
        override fun matches(value: String): Boolean = true
    }

    public object CamelCase : NameConvention {
        public val regex: Regex = Regex("^[a-z][a-zA-Z0-9]*$")
        override fun toString(): String = "camelCase"
        override fun matches(value: String): Boolean = value.matches(regex)
    }

    public object PascalCase : NameConvention {
        public val regex: Regex = Regex("^[A-Z][a-z0-9]+(?:[A-Z][a-z0-9]+)*$")
        override fun toString(): String = "PascalCase"
        override fun matches(value: String): Boolean = value.matches(regex)
    }

    public object KebabCase : NameConvention {
        public val regex: Regex = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")
        override fun toString(): String = "kebab-case"
        override fun matches(value: String): Boolean = value.matches(regex)
    }

    public data class RegexFormat(val regex: Regex) : NameConvention {
        public constructor(regex: String) : this(regex = Regex(regex))

        override fun toString(): String = regex.pattern
        override fun matches(value: String): Boolean = value.matches(regex)
    }

    public class Custom(private val filter: (String) -> Boolean) : NameConvention {
        override fun toString(): String = "custom"
        override fun matches(value: String): Boolean = filter(value)
    }
}