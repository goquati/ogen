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
import kotlin.reflect.KClass

public open class SpecsConfigBuilder {
    private val specs = mutableListOf<SpecConfig>()
    private var utilPackageName: PackageName? = null
    /** Whether any spec generates Bean Validation annotations, for models or the Spring server. */
    internal val beanValidation: Boolean
        get() = specs.any { spec ->
            spec.modelConfig.beanValidation ||
                spec.generatorConfigs.any { it is GeneratorConfig.ServerSpringV4 && it.beanValidation }
        }

    internal fun build() = SpecConfigs(
        specs = specs.toList(),
        util = SpecConfigs.Util(
            packageName = utilPackageName ?: error("utilPackageName is required"),
        ),
    )

    public fun add(
        packageName: String? = null,
        block: SpecConfingBuilder.() -> Unit,
    ): SpecsConfigBuilder = apply {
        specs += SpecConfingBuilder(
            rootPackageName = packageName,
        ).apply(block).build()
    }

    public fun utilPackageName(
        packageName: String,
    ): SpecsConfigBuilder = apply {
        utilPackageName = PackageName(packageName)
    }

    public class SpecConfingBuilder(
        public val rootPackageName: String?,
    ) {
        private var inputConfig: InputConfig? = null
        private val generatorConfigs = mutableMapOf<KClass<out GeneratorConfig>, GeneratorConfig>()
        private var validatorConfig: de.quati.ogen.plugin.intern.model.config.ValidatorConfig? = null
        private var operationIdGenerator: ((EndpointInfo) -> String?)? = null
        private val remoteRefAllowlist = mutableListOf<String>()
        private fun addConfig(config: GeneratorConfig) {
            generatorConfigs[config::class] = config
        }

        public fun operationIdGenerator(generator: (EndpointInfo) -> String?): SpecConfingBuilder = apply {
            operationIdGenerator = generator
        }

        /**
         * Lets `$ref`s load documents over HTTP(S) whose URI starts with one of [uriPrefixes], e.g.
         * `"https://schemas.example.com/"`. Without it, remote references are errors.
         */
        public fun allowRemoteRefs(vararg uriPrefixes: String): SpecConfingBuilder = apply {
            remoteRefAllowlist += uriPrefixes
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

        public fun validator(disable: Boolean = true): SpecConfingBuilder = apply {
            if (disable)
                validatorConfig = null
        }

        public fun validator(block: ValidatorConfig.() -> Unit): SpecConfingBuilder = apply {
            validatorConfig = ValidatorConfig().apply(block).build()
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

        internal fun build(): SpecConfig {
            val modelConfig = generatorConfigs[GeneratorConfig.Model::class] as? GeneratorConfig.Model
                ?: error("model config is required")
            return SpecConfig(
                inputConfig = inputConfig ?: error("input spec is required"),
                generatorConfigs = generatorConfigs.values.toList(),
                validatorConfig = validatorConfig,
                modelConfig = modelConfig,
                operationIdGenerator = operationIdGenerator,
                remoteRefAllowlist = remoteRefAllowlist.toList(),
            )
        }

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

            /**
             * Annotates the controller parameters with the Jakarta Bean Validation constraints of their schemas and
             * request bodies with `@Valid`, so Spring answers invalid requests with `400 Bad Request`. The properties
             * of the bodies are only checked if the models are generated with `model { beanValidation = true }`.
             * Requires `spring-boot-starter-validation`.
             */
            public var beanValidation: Boolean = false
            private var contextIfAnySecurity: ClassName? = null

            public fun contextIfAnySecurity(type: String): ServerSpringV4Config =
                apply { contextIfAnySecurity = type.toPoetClassName() }

            internal fun build() = GeneratorConfig.ServerSpringV4(
                packageName = packageName?.let(::PackageName) ?: error("packageName is required for server code"),
                postfix = postfix,
                addOperationContext = addOperationContext,
                apiResponse = apiResponse,
                beanValidation = beanValidation,
                contextIfAnySecurity = contextIfAnySecurity,
                skipGeneration = false,
            )
        }

        public class ClientKtorConfig internal constructor(rootPackageName: String?) {
            public var packageName: String? = rootPackageName?.let { "$it.client" }
            public var postfix: String = "Api"

            internal fun build() = GeneratorConfig.ClientKtor(
                packageName = packageName?.let(::PackageName) ?: error("packageName is required for client code"),
                postfix = postfix,
                skipGeneration = false,
            )
        }

        public class ModelConfig internal constructor(rootPackageName: String?) {
            public var packageName: String? = rootPackageName?.let { "$it.model" }
            private val typeMappings = mutableMapOf<TypeWithFormat, Type.NonPrimitiveType.Custom>()
            private val schemaMappings = mutableMapOf<ComponentName.Schema, Type.NonPrimitiveType.Custom>()
            public var skipGeneration: Boolean = false
            public var postfix: String = "Dto"

            /**
             * Annotates the model properties with the Jakarta Bean Validation constraints of their schemas, which
             * needs `jakarta.validation:jakarta.validation-api` for a JVM target. In multiplatform common code the
             * annotations of `de.quati.ogen:core` stand for them: on the JVM they are the Jakarta annotations, on
             * other platforms the compiler drops them.
             */
            public var beanValidation: Boolean = false

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
                beanValidation = beanValidation,
                typeMappings = typeMappings,
                schemaMappings = schemaMappings,
                postfix = postfix,
            )
        }

        public class ValidatorConfig {
            public var recommendations: Boolean = true
            public var failOnWarnings: Boolean = false
            private var parameterFormat = ParameterFormat().build()
            public var tagFormat: NameConvention = NameConvention.CamelCase
            public var operationIdFormat: NameConvention = NameConvention.CamelCase
            public var propertyNameFormat: NameConvention = NameConvention.CamelCase
            public var schemaNameFormat: NameConvention = NameConvention.PascalCase
            public var pathSegmentFormat: NameConvention = NameConvention.KebabCase
            public var stringEnumFormat: NameConvention = NameConvention.Any

            public fun parameterFormat(block: ParameterFormat.() -> Unit): ValidatorConfig = apply {
                parameterFormat = ParameterFormat().apply(block).build()
            }

            internal fun build() = de.quati.ogen.plugin.intern.model.config.ValidatorConfig(
                recommendations = recommendations,
                failOnWarnings = failOnWarnings,
                parameterFormat = parameterFormat,
                tagFormat = tagFormat,
                operationIdFormat = operationIdFormat,
                propertyNameFormat = propertyNameFormat,
                schemaNameFormat = schemaNameFormat,
                pathSegmentFormat = pathSegmentFormat,
                stringEnumFormat = stringEnumFormat
            )

            public class ParameterFormat {
                public var path: NameConvention = NameConvention.CamelCase
                public var query: NameConvention = NameConvention.CamelCase
                public var header: NameConvention = NameConvention.Any
                public var cookie: NameConvention = NameConvention.Any

                internal fun build() = de.quati.ogen.plugin.intern.model.config.ValidatorConfig.ParameterFormat(
                    path = path,
                    query = query,
                    header = header,
                    cookie = cookie,
                )
            }

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