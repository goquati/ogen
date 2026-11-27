package de.quati.ogen.plugin

import de.quati.ogen.plugin.intern.model.config.ValidatorConfig
import de.quati.ogen.plugin.intern.validation.ValidatedSpec
import de.quati.ogen.plugin.intern.validation.ambiguousPathWarnings
import de.quati.ogen.plugin.intern.validation.brokenPathTemplateErrors
import de.quati.ogen.plugin.intern.validation.compositionTypeErrors
import de.quati.ogen.plugin.intern.validation.contradictoryConstraintErrors
import de.quati.ogen.plugin.intern.validation.defaultOutsideEnumErrors
import de.quati.ogen.plugin.intern.validation.enumValueErrors
import de.quati.ogen.plugin.intern.validation.exclusiveFlagWithoutBoundWarnings
import de.quati.ogen.plugin.intern.validation.getWithBodyWarnings
import de.quati.ogen.plugin.intern.validation.headerUnderscoreWarnings
import de.quati.ogen.plugin.intern.validation.identicalPathErrors
import de.quati.ogen.plugin.intern.validation.ignoredNextToRefWarnings
import de.quati.ogen.plugin.intern.validation.invalidTypeErrors
import de.quati.ogen.plugin.intern.validation.namingConventionErrors
import de.quati.ogen.plugin.intern.validation.nullableIn31Warnings
import de.quati.ogen.plugin.intern.validation.nullableWithoutTypeWarnings
import de.quati.ogen.plugin.intern.validation.oneOfWithPropertiesWarnings
import de.quati.ogen.plugin.intern.validation.pathParameterErrors
import de.quati.ogen.plugin.intern.validation.patternWarnings
import de.quati.ogen.plugin.intern.validation.queryOrFragmentInPathErrors
import de.quati.ogen.plugin.intern.validation.redundantBoundWarnings
import de.quati.ogen.plugin.intern.validation.successResponseWarnings
import de.quati.ogen.plugin.intern.validation.trailingSlashWarnings
import de.quati.ogen.plugin.intern.validation.typeKeywordWarnings
import de.quati.ogen.plugin.intern.validation.undeclaredTagWarnings
import de.quati.ogen.plugin.intern.validation.undefinedRequiredPropertyErrors
import de.quati.ogen.plugin.intern.validation.undefinedSecuritySchemeErrors
import de.quati.ogen.plugin.intern.validation.unsupportedOperationWarnings
import de.quati.ogen.plugin.intern.validation.unusedSchemaWarnings
import de.quati.ogen.plugin.intern.validation.versionRequirementErrors

/**
 * The validator's rules, each with the [default] severity it has unless `rules {}` sets another. The order is the
 * order of the messages. In `rules {}`, each has a property of its own; [RuleSet]s set many at once.
 */
public enum class ValidatorRule(
    public val default: Severity,
    /** A recommendation: what may be intended, but often is a mistake. [RuleSet.Lenient] turns these off. */
    internal val recommendation: Boolean,
    /**
     * Whether the check runs only on a spec the parser found no errors in, whatever its severity: it relies on what
     * the parser checks, or would report wrongly on a spec it couldn't read completely, such as a schema as unused
     * whose `$ref` doesn't resolve. The others run on any spec the parser could read.
     */
    internal val needsValidSpec: Boolean,
    internal val check: (ValidatedSpec, ValidatorConfig) -> List<String>,
) {
    NamingConventions(Severity.Error, recommendation = false, needsValidSpec = true, ::namingConventionErrors),
    VersionRequirements(Severity.Error, ::versionRequirementErrors, needsValidSpec = true),
    BrokenPathTemplates(Severity.Error, ::brokenPathTemplateErrors, needsValidSpec = true),
    QueryOrFragmentInPaths(Severity.Error, ::queryOrFragmentInPathErrors, needsValidSpec = true),
    PathParameters(Severity.Error, ::pathParameterErrors, needsValidSpec = true),
    IdenticalPaths(Severity.Error, ::identicalPathErrors, needsValidSpec = true),
    UndefinedSecuritySchemes(Severity.Error, ::undefinedSecuritySchemeErrors, needsValidSpec = true),
    EnumValues(Severity.Error, ::enumValueErrors, needsValidSpec = true),
    DefaultOutsideEnum(Severity.Error, ::defaultOutsideEnumErrors, needsValidSpec = true),
    InvalidTypes(Severity.Error, ::invalidTypeErrors, needsValidSpec = true),
    ContradictoryConstraints(Severity.Error, ::contradictoryConstraintErrors, needsValidSpec = true),
    UndefinedRequiredProperties(Severity.Error, ::undefinedRequiredPropertyErrors, needsValidSpec = true),
    CompositionTypes(Severity.Error, ::compositionTypeErrors, needsValidSpec = true),

    TrailingSlashes(Severity.Warning, ::trailingSlashWarnings),
    MissingSuccessResponses(Severity.Warning, ::successResponseWarnings),
    UndeclaredTags(Severity.Warning, ::undeclaredTagWarnings),
    InvalidPatterns(Severity.Warning, ::patternWarnings),
    AmbiguousPaths(Severity.Warning, ::ambiguousPathWarnings),
    RedundantBounds(Severity.Warning, ::redundantBoundWarnings),
    KeywordsForOtherTypes(Severity.Warning, ::typeKeywordWarnings),
    NullableWithoutType(Severity.Warning, ::nullableWithoutTypeWarnings),
    UnusedSchemas(Severity.Warning, ::unusedSchemaWarnings, needsValidSpec = true),

    NullableIn31(Severity.Warning, ::nullableIn31Warnings, recommendation = true),
    IgnoredNextToRef(Severity.Warning, ::ignoredNextToRefWarnings, recommendation = true),
    ExclusiveFlagWithoutBound(Severity.Warning, ::exclusiveFlagWithoutBoundWarnings, recommendation = true),
    UnsupportedOperations(Severity.Warning, ::unsupportedOperationWarnings, recommendation = true),
    OneOfWithProperties(Severity.Warning, ::oneOfWithPropertiesWarnings, recommendation = true),
    GetWithBody(Severity.Warning, ::getWithBodyWarnings, recommendation = true),
    HeaderUnderscores(Severity.Warning, ::headerUnderscoreWarnings, recommendation = true),
    ;

    constructor(
        default: Severity,
        check: (ValidatedSpec) -> List<String>,
        recommendation: Boolean = false,
        needsValidSpec: Boolean = false,
    ) : this(default, recommendation, needsValidSpec, { spec, _ -> check(spec) })
}

/** Severities for many rules at once, for `rules(RuleSet.Strict) { … }`; any `Map<ValidatorRule, Severity>` will do. */
public object RuleSet {
    /** Every rule with its default severity. */
    public val Default: Map<ValidatorRule, Severity> = ValidatorRule.entries.associateWith { it.default }

    /** Every rule an error: any finding fails the build. */
    public val Strict: Map<ValidatorRule, Severity> = ValidatorRule.entries.associateWith { Severity.Error }

    /**
     * As much as possible allowed, e.g. for specs of third parties: what makes a spec invalid stays an error, other
     * problems stay warnings, and naming conventions and recommendations are off.
     */
    public val Lenient: Map<ValidatorRule, Severity> = ValidatorRule.entries.associateWith {
        when {
            it == ValidatorRule.NamingConventions || it.recommendation -> Severity.Off
            else -> it.default
        }
    }
}
