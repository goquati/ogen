package de.quati.ogen.plugin.intern.validation

import de.quati.ogen.plugin.Severity
import de.quati.ogen.plugin.intern.model.config.ValidatorConfig

/**
 * ogen's checks, each with the severity it has unless `rules {}` sets another. Recommendations are off with
 * `recommendations = false`, unless `rules {}` sets them. The order is the order of the messages.
 */
internal enum class Rule(
    val default: Severity,
    val recommendation: Boolean,
    val check: (ValidatedSpec, ValidatorConfig) -> List<String>,
) {
    NamingConventions(Severity.Error, false, ::namingConventionErrors),
    VersionRequirements(Severity.Error, ::versionRequirementErrors),
    BrokenPathTemplates(Severity.Error, ::brokenPathTemplateErrors),
    QueryOrFragmentInPaths(Severity.Error, ::queryOrFragmentInPathErrors),
    PathParameters(Severity.Error, ::pathParameterErrors),
    IdenticalPaths(Severity.Error, ::identicalPathErrors),
    UndefinedSecuritySchemes(Severity.Error, ::undefinedSecuritySchemeErrors),
    EnumValues(Severity.Error, ::enumValueErrors),
    DefaultOutsideEnum(Severity.Error, ::defaultOutsideEnumErrors),
    InvalidTypes(Severity.Error, ::invalidTypeErrors),
    ContradictoryConstraints(Severity.Error, ::contradictoryConstraintErrors),
    UndefinedRequiredProperties(Severity.Error, ::undefinedRequiredPropertyErrors),
    CompositionTypes(Severity.Error, ::compositionTypeErrors),

    TrailingSlashes(Severity.Warning, ::trailingSlashWarnings),
    MissingSuccessResponses(Severity.Warning, ::successResponseWarnings),
    UndeclaredTags(Severity.Warning, ::undeclaredTagWarnings),
    InvalidPatterns(Severity.Warning, ::patternWarnings),
    AmbiguousPaths(Severity.Warning, ::ambiguousPathWarnings),
    RedundantBounds(Severity.Warning, ::redundantBoundWarnings),
    KeywordsForOtherTypes(Severity.Warning, ::typeKeywordWarnings),
    NullableWithoutType(Severity.Warning, ::nullableWithoutTypeWarnings),
    UnusedSchemas(Severity.Warning, ::unusedSchemaWarnings),

    NullableIn31(Severity.Warning, ::nullableIn31Warnings, recommendation = true),
    IgnoredNextToRef(Severity.Warning, ::ignoredNextToRefWarnings, recommendation = true),
    ExclusiveFlagWithoutBound(Severity.Warning, ::exclusiveFlagWithoutBoundWarnings, recommendation = true),
    UnsupportedOperations(Severity.Warning, ::unsupportedOperationWarnings, recommendation = true),
    OneOfWithProperties(Severity.Warning, ::oneOfWithPropertiesWarnings, recommendation = true),
    GetWithBody(Severity.Warning, ::getWithBodyWarnings, recommendation = true),
    HeaderUnderscores(Severity.Warning, ::headerUnderscoreWarnings, recommendation = true),
    ;

    constructor(default: Severity, check: (ValidatedSpec) -> List<String>, recommendation: Boolean = false) :
        this(default, recommendation, { spec, _ -> check(spec) })

    /**
     * Checks that are errors by default run only on a spec the parser found no errors in; they may rely on what the
     * parser checks. The others run on any spec the parser could read.
     */
    val needsValidSpec: Boolean get() = default == Severity.Error
}
