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
    /**
     * Whether the check runs only on a spec the parser found no errors in, whatever its severity: it relies on what
     * the parser checks, or would report wrongly on a spec it couldn't read completely, such as a schema as unused
     * whose `$ref` doesn't resolve. The others run on any spec the parser could read.
     */
    val needsValidSpec: Boolean,
    val check: (ValidatedSpec, ValidatorConfig) -> List<String>,
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
