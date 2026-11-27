package de.quati.ogen.plugin

public sealed interface NameConvention {
    public fun matches(value: String): Boolean

    /** Options of every built-in convention: names it accepts as they are. */
    @OgenDsl
    public open class Builder internal constructor() {
        internal val exceptions: MutableSet<String> = linkedSetOf()

        /** Accepts these exact names even if they don't match the convention. Can be called several times. */
        public fun except(vararg names: String) {
            exceptions.addAll(names)
        }
    }

    /** Options of [CamelCase] and [PascalCase]. */
    @OgenDsl
    public class CaseBuilder internal constructor() : Builder() {
        /**
         * With `true` (the default), a run of capitals counts as one word (`UUID`, `HTTPError`, `apiID`); with `false`,
         * every word starts with one capital followed by lower-case letters and digits; only the last word may be a
         * single capital (`PointX`, `getA`), since two of them in a row would be an acronym.
         */
        public var acronyms: Boolean = true
    }

    public object Any : NameConvention {
        override fun toString(): String = "any"
        override fun matches(value: String): Boolean = true
    }

    /** camelCase, acronyms allowed; `CamelCase { … }` sets [CaseBuilder.acronyms] and exceptions. */
    public object CamelCase : NameConvention by camelCase(CaseBuilder()) {
        public operator fun invoke(block: CaseBuilder.() -> Unit): NameConvention = camelCase(CaseBuilder().apply(block))
        override fun toString(): String = "camelCase"
    }

    /** PascalCase, acronyms allowed; `PascalCase { … }` sets [CaseBuilder.acronyms] and exceptions. */
    public object PascalCase : NameConvention by pascalCase(CaseBuilder()) {
        public operator fun invoke(block: CaseBuilder.() -> Unit): NameConvention = pascalCase(CaseBuilder().apply(block))
        override fun toString(): String = "PascalCase"
    }

    /** kebab-case; `KebabCase { … }` sets exceptions. */
    public object KebabCase : NameConvention by kebabCase(Builder()) {
        public operator fun invoke(block: Builder.() -> Unit): NameConvention = kebabCase(Builder().apply(block))
        override fun toString(): String = "kebab-case"
    }

    /** Names that match [regex]; the block sets exceptions. */
    public class RegexFormat(public val regex: Regex, block: Builder.() -> Unit = {}) : NameConvention {
        public constructor(regex: String, block: Builder.() -> Unit = {}) : this(Regex(regex), block)

        private val exceptions: Set<String> = Builder().apply(block).exceptions.toSet()

        override fun toString(): String = regex.pattern
        override fun matches(value: String): Boolean = value in exceptions || value.matches(regex)
    }

    public class Custom(private val filter: (String) -> Boolean) : NameConvention {
        override fun toString(): String = "custom"
        override fun matches(value: String): Boolean = filter(value)
    }
}

/** A built-in convention with its options applied. */
private class ConfiguredConvention(
    private val name: String,
    private val regex: Regex,
    private val exceptions: Set<String>,
) : NameConvention {
    override fun toString(): String = name
    override fun matches(value: String): Boolean = value in exceptions || value.matches(regex)
}

private fun camelCase(options: NameConvention.CaseBuilder): NameConvention = ConfiguredConvention(
    name = if (options.acronyms) "camelCase" else "camelCase without acronyms",
    regex = if (options.acronyms) Regex("^[a-z][a-zA-Z0-9]*$") else Regex("^[a-z][a-z0-9]*(?:[A-Z][a-z0-9]+)*[A-Z]?$"),
    exceptions = options.exceptions.toSet(),
)

private fun pascalCase(options: NameConvention.CaseBuilder): NameConvention = ConfiguredConvention(
    name = if (options.acronyms) "PascalCase" else "PascalCase without acronyms",
    regex = if (options.acronyms) Regex("^[A-Z][a-zA-Z0-9]*$") else Regex("^(?=.)(?:[A-Z][a-z0-9]+)*[A-Z]?$"),
    exceptions = options.exceptions.toSet(),
)

private fun kebabCase(options: NameConvention.Builder): NameConvention = ConfiguredConvention(
    name = "kebab-case",
    regex = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$"),
    exceptions = options.exceptions.toSet(),
)
