package de.quati.ogen.plugin

import com.squareup.kotlinpoet.ClassName
import de.quati.kotlin.util.poet.PackageName
import de.quati.ogen.plugin.intern.model.ComponentName
import de.quati.ogen.plugin.intern.model.config.SpecConfigs
import de.quati.ogen.plugin.intern.model.Type
import de.quati.ogen.plugin.intern.model.TypeWithFormat
import de.quati.ogen.plugin.intern.model.config.GeneratorConfig
import de.quati.ogen.plugin.intern.model.config.SpecConfig
import java.nio.file.Path
import kotlin.collections.mutableMapOf
import kotlin.io.path.Path
import kotlin.io.path.isRegularFile
import kotlin.reflect.KClass

public open class SpecsConfigBuilder {
    private val specs = mutableListOf<SpecConfig>()
    internal fun build() = SpecConfigs(specs = specs.toList())

    public fun addSpec(
        apiFile: String,
        packageName: String? = null,
        block: SpecConfingBuilder.() -> Unit,
    ): SpecsConfigBuilder = apply {
        specs += SpecConfingBuilder(
            apiFile = Path(apiFile),
            rootPackageName = packageName,
        ).apply(block).build()
    }

    public class SpecConfingBuilder(
        private val apiFile: Path,
        public val rootPackageName: String?,
    ) {
        private val generatorConfigs = mutableMapOf<KClass<out GeneratorConfig>, GeneratorConfig>()
        private var validatorConfig: de.quati.ogen.plugin.intern.model.config.ValidatorConfig? =
            ValidatorConfig().build()

        private fun addConfig(config: GeneratorConfig) {
            generatorConfigs[config::class] = config
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

        public fun shared(
            block: SharedConfig.() -> Unit = {},
        ): SpecConfingBuilder = apply {
            val config = SharedConfig(rootPackageName = rootPackageName).apply(block).build()
            addConfig(config)
        }

        public fun serverSpringV4(
            block: ServerSpringV4Config.() -> Unit = {},
        ): SpecConfingBuilder = apply {
            val config = ServerSpringV4Config(rootPackageName = rootPackageName).apply(block).build()
            addConfig(config)
        }

        public fun ktorClient(
            block: KtorClientConfig.() -> Unit = {},
        ): SpecConfingBuilder = apply {

            val config = KtorClientConfig(rootPackageName = rootPackageName).apply(block).build()

            generatorConfigs[config::class] = config
        }

        internal fun build(): SpecConfig {
            val sharedConfig = generatorConfigs.computeIfAbsent(GeneratorConfig.Shared::class) {
                SharedConfig(rootPackageName = rootPackageName).build()
            } as GeneratorConfig.Shared
            val modelConfig = generatorConfigs[GeneratorConfig.Model::class] as? GeneratorConfig.Model
                ?: error("model config is required")
            return SpecConfig(
                apiFile = apiFile.also {
                    require(it.isRegularFile()) { "apiFile '$it' does not exist" }
                },
                generatorConfigs = generatorConfigs.values.toList(),
                validatorConfig = validatorConfig,
                modelConfig = modelConfig,
                sharedConfig = sharedConfig
            )
        }

        public class ServerSpringV4Config internal constructor(rootPackageName: String?) {
            public var packageName: String? = rootPackageName?.let { "$it.server" }
            public var postfix: String = "Api"
            public var addOperationContext: Boolean = false
            private var contextIfAnySecurity: ClassName? = null

            public fun contextIfAnySecurity(type: String): ServerSpringV4Config =
                apply { contextIfAnySecurity = type.toPoetClassName() }

            internal fun build() = GeneratorConfig.ServerSpringV4(
                packageName = packageName?.let(::PackageName) ?: error("packageName is required for server code"),
                postfix = postfix,
                addOperationContext = addOperationContext,
                contextIfAnySecurity = contextIfAnySecurity,
                skipGeneration = false,
            )
        }

        public class KtorClientConfig internal constructor(private val rootPackageName: String?) {
            public var packageName: String? = rootPackageName?.let { "$it.client" }
            public var postfix: String = "Api"
            private var util: GeneratorConfig.KtorClient.Util? = null
            public fun util(block: Util.() -> Unit): KtorClientConfig = apply {
                util = Util(rootPackageName = rootPackageName).apply(block).build()
            }

            internal fun build() = GeneratorConfig.KtorClient(
                packageName = packageName?.let(::PackageName) ?: error("packageName is required for client code"),
                postfix = postfix,
                util = util ?: Util(rootPackageName = rootPackageName).build(),
                skipGeneration = false,
            )

            public class Util internal constructor(rootPackageName: String?) {
                public var packageName: String? = rootPackageName?.let { "$it.client.util" }
                internal fun build() = GeneratorConfig.KtorClient.Util(
                    packageName = packageName?.let(::PackageName)
                        ?: error("packageName is required for client util code"),
                    skipGeneration = false,
                )
            }
        }

        public class SharedConfig internal constructor(rootPackageName: String?) {
            public var packageName: String? = rootPackageName?.let { "$it.shared" }
            public var skipGeneration: Boolean = false

            internal fun build() = GeneratorConfig.Shared(
                packageName = packageName?.let(::PackageName) ?: error("packageName is required for shared code"),
                skipGeneration = skipGeneration,
            )
        }

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
                val name = ComponentName.Schema.parse(schema)
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