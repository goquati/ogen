package de.quati.ogen.plugin

import com.squareup.kotlinpoet.ClassName
import de.quati.kotlin.util.poet.PackageName
import de.quati.ogen.plugin.intern.model.ComponentName
import de.quati.ogen.plugin.intern.model.config.SpecConfigs
import de.quati.ogen.plugin.intern.model.Type
import de.quati.ogen.plugin.intern.model.TypeWithFormat
import de.quati.ogen.plugin.intern.model.config.GeneratorConfig
import de.quati.ogen.plugin.intern.model.config.InputConfig
import de.quati.ogen.plugin.intern.model.config.SpecConfig
import kotlin.collections.mutableMapOf
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * Marks the blocks of the `ogen {}` DSL, so that a block can't set a property of an enclosing block by accident,
 * e.g. a removed `model { beanValidation = true }` setting the spec's `beanValidation`.
 */
@DslMarker
public annotation class OgenDsl

@OgenDsl
public open class SpecsConfigBuilder {
    private val specs = mutableListOf<SpecConfig>()
    private var validator: SpecConfingBuilder.ValidatorConfig? = null
    private var utilPackageName: PackageName? = null
    /** Whether any spec generates Bean Validation annotations, for models or the Spring server. */
    internal val beanValidation: Boolean
        get() = specs.any { it.beanValidation }

    internal fun build() = SpecConfigs(
        // The same instances on every call, so that the tasks share each spec's parse result.
        specs = specs.toList(),
        util = SpecConfigs.Util(
            packageName = utilPackageName ?: error("utilPackageName is required"),
        ),
    )

    public fun add(
        packageName: String? = null,
        block: SpecConfingBuilder.() -> Unit,
    ): SpecsConfigBuilder = apply {
        val builder = SpecConfingBuilder(rootPackageName = packageName).apply(block)
        // The validator block of `ogen {}` may come after the spec, so its settings are read when the validator runs.
        specs += builder.build { validator }
    }

    /**
     * Validator settings every spec inherits; every spec is validated, without this block with the defaults. A spec's
     * `validator {}` changes only the settings it sets; `validator { enabled = false }` turns validation off for one
     * spec, or here for every spec that doesn't turn it on.
     */
    public fun validator(block: SpecConfingBuilder.ValidatorConfig.() -> Unit): SpecsConfigBuilder = apply {
        validator = (validator ?: SpecConfingBuilder.ValidatorConfig()).apply(block)
    }

    public fun utilPackageName(
        packageName: String,
    ): SpecsConfigBuilder = apply {
        utilPackageName = PackageName(packageName)
    }

    @OgenDsl
    public class SpecConfingBuilder(
        public val rootPackageName: String?,
    ) {
        private var inputConfig: InputConfig? = null
        private val generatorConfigs = mutableMapOf<KClass<out GeneratorConfig>, GeneratorConfig>()
        private var validatorConfig: ValidatorConfig? = null
        private var operationIdGenerator: ((EndpointInfo) -> String?)? = null

        /**
         * Annotates the models with the Jakarta Bean Validation constraints of their schemas, and the Spring
         * controller parameters too, with `@Valid` on request bodies, so Spring answers invalid requests with
         * `400 Bad Request`. Generators without validation, such as `clientKtor`, ignore it. The models need
         * `jakarta.validation:jakarta.validation-api` for a JVM target; in multiplatform common code the annotations
         * of `de.quati.ogen:core` stand for them: on the JVM they are the Jakarta annotations, on other platforms the
         * compiler drops them. The server needs `spring-boot-starter-validation`. If the models are generated in
         * another module (`model { skipGeneration = true }`), set it there and here.
         */
        public var beanValidation: Boolean = false

        private fun addConfig(config: GeneratorConfig) {
            generatorConfigs[config::class] = config
        }

        public fun operationIdGenerator(generator: (EndpointInfo) -> String?): SpecConfingBuilder = apply {
            operationIdGenerator = generator
        }

        public fun specFile(path: String): SpecConfingBuilder = apply {
            val path = Path(path).also { check(it.isRegularFile()) { "spec file '$path' does not exist" } }
            inputConfig = InputConfig.File(path = path)
        }

        public fun specMerge(
            path: String,
            mergeFileName: String = "merged",
            mergedFileInfoTitle: String = "MergedSpec",
            mergedFileInfoDescription: String? = null,
            mergedFileInfoVersion: String = "1.0.0",
            mergedFileAdditional: Map<String, Any?> = emptyMap(),
        ): SpecConfingBuilder = apply {
            val path = Path(path).also { check(it.isDirectory()) { "spec directory '$path' does not exist" } }
            inputConfig = InputConfig.Merge(
                directoryPath = path,
                mergeFileName = mergeFileName,
                baseConfig = InputConfig.Merge.BaseConfig.Data(
                    infoTitle = mergedFileInfoTitle,
                    infoDescription = mergedFileInfoDescription,
                    infoVersion = mergedFileInfoVersion,
                    additional = mergedFileAdditional,
                ),
            )
        }

        public fun specMerge(
            path: String,
            baseSpec: String,
            mergeFileName: String = "merged",
        ): SpecConfingBuilder = apply {
            val path = Path(path).also { check(it.isDirectory()) { "spec directory '$path' does not exist" } }
            inputConfig = InputConfig.Merge(
                directoryPath = path,
                mergeFileName = mergeFileName,
                baseConfig = InputConfig.Merge.BaseConfig.File(
                    path = Path(baseSpec),
                ),
            )
        }

        /**
         * The validator settings of this spec. They start from those of `validator {}` in `ogen {}`, if there is one,
         * else from the defaults; this block changes only what it sets.
         */
        public fun validator(block: ValidatorConfig.() -> Unit): SpecConfingBuilder = apply {
            validatorConfig = (validatorConfig ?: ValidatorConfig()).apply(block)
        }

        public fun model(
            block: ModelConfig.() -> Unit = {},
        ): SpecConfingBuilder = apply {
            val config = ModelConfig(rootPackageName = rootPackageName).apply(block).build()
            addConfig(config)
        }

        public fun serverSpringV4(
            block: ServerSpringV4Config.() -> Unit = {},
        ): SpecConfingBuilder = apply {
            val config = ServerSpringV4Config(rootPackageName = rootPackageName).apply(block).build()
            addConfig(config)
        }

        public fun clientKtor(
            block: ClientKtorConfig.() -> Unit = {},
        ): SpecConfingBuilder = apply {

            val config = ClientKtorConfig(rootPackageName = rootPackageName).apply(block).build()

            generatorConfigs[config::class] = config
        }

        internal fun build(parentValidator: () -> ValidatorConfig?): SpecConfig {
            val modelConfig = generatorConfigs[GeneratorConfig.Model::class] as? GeneratorConfig.Model
                ?: error("model config is required")
            return SpecConfig(
                inputConfig = inputConfig ?: error("input spec is required"),
                generatorConfigs = generatorConfigs.values.toList(),
                resolveValidatorConfig = { ValidatorConfig.resolve(own = validatorConfig, parent = parentValidator()) },
                modelConfig = modelConfig,
                beanValidation = beanValidation,
                operationIdGenerator = operationIdGenerator,
            )
        }

        @OgenDsl
        public class ServerSpringV4Config internal constructor(rootPackageName: String?) {
            public var packageName: String? = rootPackageName?.let { "$it.server" }
            public var postfix: String = "Api"
            public var addOperationContext: Boolean = false

            /**
             * Controller functions return typed responses implementing `de.quati.ogen.server.spring.ApiResponse`
             * (a sealed interface if an operation defines multiple responses) instead of `ResponseEntity`.
             * Requires the `de.quati.ogen:server-spring` library.
             */
            public var apiResponse: Boolean = false
            private var contextIfAnySecurity: ClassName? = null

            public fun contextIfAnySecurity(type: String): ServerSpringV4Config =
                apply { contextIfAnySecurity = type.toPoetClassName() }

            internal fun build() = GeneratorConfig.ServerSpringV4(
                packageName = packageName?.let(::PackageName) ?: error("packageName is required for server code"),
                postfix = postfix,
                addOperationContext = addOperationContext,
                apiResponse = apiResponse,
                contextIfAnySecurity = contextIfAnySecurity,
                skipGeneration = false,
            )
        }

        @OgenDsl
        public class ClientKtorConfig internal constructor(rootPackageName: String?) {
            public var packageName: String? = rootPackageName?.let { "$it.client" }
            public var postfix: String = "Api"

            internal fun build() = GeneratorConfig.ClientKtor(
                packageName = packageName?.let(::PackageName) ?: error("packageName is required for client code"),
                postfix = postfix,
                skipGeneration = false,
            )
        }

        @OgenDsl
        public class ModelConfig internal constructor(rootPackageName: String?) {
            public var packageName: String? = rootPackageName?.let { "$it.model" }
            private val typeMappings = mutableMapOf<TypeWithFormat, Type.NonPrimitiveType.Custom>()
            private val schemaMappings = mutableMapOf<ComponentName.Schema, Type.NonPrimitiveType.Custom>()
            public var skipGeneration: Boolean = false
            public var postfix: String = "Dto"

            public fun typeMapping(
                type: String,
                clazz: String,
                serializerObject: String? = null,
            ): ModelConfig = apply {
                val typeWithFormat = type.split('+').let {
                    when (it.size) {
                        1 -> TypeWithFormat(type = it.single(), format = null)
                        2 -> TypeWithFormat(type = it.first(), format = it.last())
                        else -> error("unknown type format '$type' expected formats <type> or <type>+<format>")
                    }
                }
                typeMappings[typeWithFormat] = parseCustomType(clazz = clazz, serializerObject = serializerObject)
            }

            public fun schemaMapping(
                schema: String,
                clazz: String,
                serializerObject: String? = null,
            ): ModelConfig = apply {
                require(schema.isNotBlank()) { "schema must not be blank" }
                val name = ComponentName.Schema.Nested.parse(schema.split('.'))
                schemaMappings[name] = parseCustomType(clazz = clazz, serializerObject = serializerObject)
            }

            internal fun build() = GeneratorConfig.Model(
                packageName = packageName?.let(::PackageName) ?: error("packageName is required for model code"),
                skipGeneration = skipGeneration,
                typeMappings = typeMappings,
                schemaMappings = schemaMappings,
                postfix = postfix,
            )
        }

        /**
         * The validator's settings. In a spec's `validator {}`, they start from those of `validator {}` in `ogen {}`,
         * and only what the block sets changes, also inside `naming {}`, its `parameter {}` and `rules {}`. A property
         * reads what this block set, or else the default.
         */
        @OgenDsl
        public class ValidatorConfig {
            private val enabledSetting = Setting { true }
            private val naming = Naming()
            private val rules = Rules()

            /** `false` turns validation off for this spec, or, in `ogen {}`, for every spec that doesn't set it. */
            public var enabled: Boolean by enabledSetting

            /**
             * The naming conventions names must follow. How a name that doesn't follow one is reported is the
             * severity of the rule [Rules.namingConventions].
             */
            public fun naming(block: Naming.() -> Unit): ValidatorConfig = apply {
                naming.apply(block)
            }

            /** The severity of each rule: errors fail the build, warnings are only reported. */
            public fun rules(block: Rules.() -> Unit): ValidatorConfig = apply {
                rules.apply(block)
            }

            /** The severities of [set], such as [RuleSet.Strict], then those [block] sets. */
            public fun rules(set: Map<ValidatorRule, Severity>, block: Rules.() -> Unit = {}): ValidatorConfig = apply {
                rules.use(set)
                rules.apply(block)
            }

            /**
             * One naming convention per kind of name. A property reads what this block set, or else the default.
             * The severity of a violation is set by the rule [Rules.namingConventions].
             */
            @OgenDsl
            public class Naming {
                private val schemaSetting = Setting<NameConvention> { NameConvention.PascalCase }
                private val propertySetting = Setting<NameConvention> { NameConvention.CamelCase }
                private val tagSetting = Setting<NameConvention> { NameConvention.CamelCase }
                private val operationIdSetting = Setting<NameConvention> { NameConvention.CamelCase }
                private val pathSegmentSetting = Setting<NameConvention> { NameConvention.KebabCase }
                private val pathExtensionSetting = Setting<NameConvention> { NameConvention.KebabCase }
                private val enumValueSetting = Setting<NameConvention> { NameConvention.Any }
                private val parameter = Parameter()

                /** Names of the schemas under `components.schemas`. */
                public var schema: NameConvention by schemaSetting

                /** Names of the properties of object schemas. */
                public var property: NameConvention by propertySetting
                public var tag: NameConvention by tagSetting
                public var operationId: NameConvention by operationIdSetting

                /**
                 * The names in the paths: a segment split at `:` (`{id}:cancel`), each piece up to its first `.`. A
                 * part matches if it does as written, or with every `{variable}` in it replaced by `xx`, or by `Xx`;
                 * a part that is only variables is not checked.
                 */
                public var pathSegment: NameConvention by pathSegmentSetting

                /** The extensions in the paths: the parts after the first `.` of a name (`csv`, `tar`, `gz`). */
                public var pathExtension: NameConvention by pathExtensionSetting

                /** The values of string enums. */
                public var enumValue: NameConvention by enumValueSetting

                /** Parameter names, per location. */
                public fun parameter(block: Parameter.() -> Unit): Naming = apply {
                    parameter.apply(block)
                }

                internal fun resolve(parent: Naming?) =
                    de.quati.ogen.plugin.intern.model.config.ValidatorConfig.Naming(
                        schema = pick(parent) { schemaSetting },
                        property = pick(parent) { propertySetting },
                        tag = pick(parent) { tagSetting },
                        operationId = pick(parent) { operationIdSetting },
                        pathSegment = pick(parent) { pathSegmentSetting },
                        pathExtension = pick(parent) { pathExtensionSetting },
                        enumValue = pick(parent) { enumValueSetting },
                        parameter = parameter.resolve(parent?.parameter),
                    )

                @OgenDsl
                public class Parameter {
                    private val pathSetting = Setting<NameConvention> { NameConvention.CamelCase }
                    private val querySetting = Setting<NameConvention> { NameConvention.CamelCase }
                    private val headerSetting = Setting<NameConvention> { NameConvention.Any }
                    private val cookieSetting = Setting<NameConvention> { NameConvention.Any }

                    public var path: NameConvention by pathSetting

                    /** Query parameters, and the name of a 3.2 `querystring` parameter. */
                    public var query: NameConvention by querySetting
                    public var header: NameConvention by headerSetting
                    public var cookie: NameConvention by cookieSetting

                    internal fun resolve(parent: Parameter?) =
                        de.quati.ogen.plugin.intern.model.config.ValidatorConfig.Naming.Parameter(
                            path = pick(parent) { pathSetting },
                            query = pick(parent) { querySetting },
                            header = pick(parent) { headerSetting },
                            cookie = pick(parent) { cookieSetting },
                        )
                }
            }

            /**
             * One property per rule, set to [Severity.Off], [Severity.Warning] or [Severity.Error]. A property reads
             * what this block set, or else the rule's default.
             */
            @OgenDsl
            public class Rules {
                internal val severities = mutableMapOf<ValidatorRule, Severity>()

                /** Sets the severities of [set], such as [RuleSet.Strict]; the other rules keep theirs. */
                public fun use(set: Map<ValidatorRule, Severity>): Rules = apply { severities += set }

                public operator fun set(rule: ValidatorRule, severity: Severity) {
                    severities[rule] = severity
                }

                public operator fun get(rule: ValidatorRule): Severity = severities[rule] ?: rule.default

                private fun rule(rule: ValidatorRule) = object : ReadWriteProperty<Rules, Severity> {
                    override fun getValue(thisRef: Rules, property: KProperty<*>) = severities[rule] ?: rule.default
                    override fun setValue(thisRef: Rules, property: KProperty<*>, value: Severity) {
                        severities[rule] = value
                    }
                }

                // Errors by default.

                /** Fields the spec's version requires: `paths`, `responses` and `items` in 3.0, a response's `description`. */
                public var versionRequirements: Severity by rule(ValidatorRule.VersionRequirements)

                /** Path templates with an empty `{}`, an unclosed `{` or a stray `}`. */
                public var brokenPathTemplates: Severity by rule(ValidatorRule.BrokenPathTemplates)

                /** A query string or fragment in a path key. */
                public var queryOrFragmentInPaths: Severity by rule(ValidatorRule.QueryOrFragmentInPaths)

                /** Path parameters missing from the path template, and template variables without a parameter. */
                public var pathParameters: Severity by rule(ValidatorRule.PathParameters)

                /** Paths that differ only in their variables' names. */
                public var identicalPaths: Severity by rule(ValidatorRule.IdenticalPaths)

                /** Security requirements naming a scheme `components.securitySchemes` doesn't define. */
                public var undefinedSecuritySchemes: Severity by rule(ValidatorRule.UndefinedSecuritySchemes)

                /** Enum values that don't match their schema's type or are listed twice. */
                public var enumValues: Severity by rule(ValidatorRule.EnumValues)

                /** A `default` its `enum` doesn't list. */
                public var defaultOutsideEnum: Severity by rule(ValidatorRule.DefaultOutsideEnum)

                /** A `type` that isn't a JSON Schema type. */
                public var invalidTypes: Severity by rule(ValidatorRule.InvalidTypes)

                /** Contradictory constraints such as `minimum` above `maximum`. */
                public var contradictoryConstraints: Severity by rule(ValidatorRule.ContradictoryConstraints)

                /** `required` names without a matching property. */
                public var undefinedRequiredProperties: Severity by rule(ValidatorRule.UndefinedRequiredProperties)

                /** Compositions no value can match. */
                public var compositionTypes: Severity by rule(ValidatorRule.CompositionTypes)

                // Warnings by default.

                /** Paths with a trailing slash. */
                public var trailingSlashes: Severity by rule(ValidatorRule.TrailingSlashes)

                /** Operations without a 2xx or 3xx response. */
                public var missingSuccessResponses: Severity by rule(ValidatorRule.MissingSuccessResponses)

                /** Operation tags missing from the top-level `tags`, when it declares any. */
                public var undeclaredTags: Severity by rule(ValidatorRule.UndeclaredTags)

                /** `pattern` regexes and `patternProperties` keys that don't compile as Java regexes. */
                public var invalidPatterns: Severity by rule(ValidatorRule.InvalidPatterns)

                /** Two paths that share a method where neither is more concrete. */
                public var ambiguousPaths: Severity by rule(ValidatorRule.AmbiguousPaths)

                /** In 3.1 and later, `maximum` next to `exclusiveMaximum` or `minimum` next to `exclusiveMinimum`. */
                public var redundantBounds: Severity by rule(ValidatorRule.RedundantBounds)

                /** Keywords that apply to none of the schema's types, such as `maxLength` on an integer. */
                public var keywordsForOtherTypes: Severity by rule(ValidatorRule.KeywordsForOtherTypes)

                /** `nullable` without a `type` in 3.0. */
                public var nullableWithoutType: Severity by rule(ValidatorRule.NullableWithoutType)

                /** Component schemas no operation uses; `x-ogen-keep: true` on a schema keeps it on purpose. */
                public var unusedSchemas: Severity by rule(ValidatorRule.UnusedSchemas)

                // Recommendations: warnings by default, off in [RuleSet.Lenient].

                /** `nullable` in 3.1 and later, which has no effect there. */
                public var nullableIn31: Severity by rule(ValidatorRule.NullableIn31)

                /**
                 * Schema keywords the spec's OpenAPI version deprecates: `example` since 3.1 (use `examples`), and the
                 * XML Object's `attribute` and `wrapped` since 3.2 (use `nodeType`).
                 */
                public var deprecatedKeywords: Severity by rule(ValidatorRule.DeprecatedKeywords)

                /** Keywords next to a `$ref` that are ignored. */
                public var ignoredNextToRef: Severity by rule(ValidatorRule.IgnoredNextToRef)

                /** `exclusiveMinimum: true` without `minimum`, or `exclusiveMaximum: true` without `maximum`, in 3.0. */
                public var exclusiveFlagWithoutBound: Severity by rule(ValidatorRule.ExclusiveFlagWithoutBound)

                /** Operations ogen generates no code for: 3.2's `additionalOperations`. */
                public var unsupportedOperations: Severity by rule(ValidatorRule.UnsupportedOperations)

                /** `properties` next to `oneOf`. */
                public var oneOfWithProperties: Severity by rule(ValidatorRule.OneOfWithProperties)

                /** A request body on GET or HEAD. */
                public var getWithBody: Severity by rule(ValidatorRule.GetWithBody)

                /** Header names with `_`. */
                public var headerUnderscores: Severity by rule(ValidatorRule.HeaderUnderscores)

                /** Names that don't follow the conventions of `validator { naming {} }`. */
                public var namingConventions: Severity by rule(ValidatorRule.NamingConventions)

                // Off by default.

                /**
                 * Operations under `paths` without an `operationId`, for projects that choose every method name in the
                 * spec. [RuleSet.Strict] turns it on, too.
                 */
                public var operationIdRequired: Severity by rule(ValidatorRule.OperationIdRequired)
            }

            internal companion object {
                /**
                 * The settings of a spec: what its own block sets, else what the block in `ogen {}` sets, else the
                 * default. Null if validation is off.
                 */
                fun resolve(own: ValidatorConfig?, parent: ValidatorConfig?): de.quati.ogen.plugin.intern.model.config.ValidatorConfig? {
                    val config = own ?: parent ?: ValidatorConfig()
                    val ownParent = parent.takeIf { own != null }
                    if (!config.pick(ownParent) { enabledSetting }) return null
                    return de.quati.ogen.plugin.intern.model.config.ValidatorConfig(
                        naming = config.naming.resolve(ownParent?.naming),
                        rules = ownParent?.rules?.severities.orEmpty() + config.rules.severities,
                    )
                }
            }
        }

        /** A setting that knows whether it was set, so that an unset one can be inherited. */
        private class Setting<T : Any>(val default: () -> T) : ReadWriteProperty<Any, T> {
            var explicit: T? = null
                private set

            override fun getValue(thisRef: Any, property: KProperty<*>): T = explicit ?: default()
            override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
                explicit = value
            }
        }

        private companion object {
            /** What this config sets, else what [parent] sets, else the default. */
            private fun <C : Any, T : Any> C.pick(parent: C?, setting: C.() -> Setting<T>): T =
                setting().explicit ?: parent?.setting()?.explicit ?: setting().default()
        }
    }

    private companion object {
        private fun String.toPoetClassName(): ClassName {
            if ('.' !in this) error("expected class name format <package>.<class-name>, but get '$this'")
            return ClassName(substringBeforeLast('.'), substringAfterLast('.'))
        }

        private fun parseCustomType(
            clazz: String,
            serializerObject: String?,
        ): Type.NonPrimitiveType.Custom {
            val className = clazz.toPoetClassName()
            return Type.NonPrimitiveType.Custom(
                packageName = className.packageName.let(::PackageName),
                simpleNames = className.simpleNames,
                serializer = serializerObject?.toPoetClassName(),
            )
        }
    }
}