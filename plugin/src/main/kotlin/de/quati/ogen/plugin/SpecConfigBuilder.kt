package de.quati.ogen.plugin

import com.squareup.kotlinpoet.ClassName
import de.quati.kotlin.util.poet.PackageName
import de.quati.ogen.plugin.intern.model.ComponentName
import de.quati.ogen.plugin.intern.model.config.SpecConfigs
import de.quati.ogen.plugin.intern.model.Type
import de.quati.ogen.plugin.intern.model.TypeWithFormat
import de.quati.ogen.plugin.intern.model.config.SpecConfig
import java.nio.file.Path
import kotlin.collections.mutableMapOf
import kotlin.io.path.Path
import kotlin.io.path.isRegularFile

public open class SpecConfigBuilder {
    private val specs = mutableListOf<SpecConfig>()
    internal fun build() = SpecConfigs(specs = specs.toList())

    public fun addSpec(
        apiFile: String,
        packageName: String? = null,
        block: GeneratorConfig.() -> Unit,
    ): SpecConfigBuilder = apply {
        specs += GeneratorConfig(
            apiFile = Path(apiFile),
            rootPackageName = packageName,
        ).apply(block).build()
    }

    public class GeneratorConfig(
        private val apiFile: Path,
        public val rootPackageName: String?,
    ) {
        private var modelConfig: de.quati.ogen.plugin.intern.model.config.ModelConfig? = null
        private var sharedConfig: de.quati.ogen.plugin.intern.model.config.SharedConfig? = null
        private val generatorConfigs = mutableListOf<de.quati.ogen.plugin.intern.model.config.GeneratorConfig>()
        private var validatorConfig: de.quati.ogen.plugin.intern.model.config.ValidatorConfig? =
            ValidatorConfig().build()

        public fun validator(disable: Boolean = true): GeneratorConfig = apply {
            if (disable)
                validatorConfig = null
        }

        public fun validator(block: ValidatorConfig.() -> Unit): GeneratorConfig = apply {
            validatorConfig = ValidatorConfig().apply(block).build()
        }

        public fun model(
            block: ModelConfig.() -> Unit = {},
        ): GeneratorConfig = apply {
            modelConfig = ModelConfig(rootPackageName = rootPackageName).apply(block).build()
        }

        public fun shared(
            block: SharedConfig.() -> Unit = {},
        ): GeneratorConfig = apply {
            sharedConfig = SharedConfig(rootPackageName = rootPackageName).apply(block).build()
        }

        public fun serverSpringV4(
            block: ServerSpringV4Config.() -> Unit = {},
        ): GeneratorConfig = apply {
            generatorConfigs.add(
                ServerSpringV4Config(rootPackageName = rootPackageName).apply(block).build()
            )
        }

        internal fun build(): SpecConfig {
            val modelConfig = modelConfig ?: error("model config is required")
            return SpecConfig(
                apiFile = apiFile.also {
                    require(it.isRegularFile()) { "apiFile '$it' does not exist" }
                },
                modelConfig = modelConfig,
                sharedConfig = sharedConfig ?: SharedConfig(rootPackageName = rootPackageName).build(),
                generatorConfigs = generatorConfigs,
                validatorConfig = validatorConfig,
            )
        }

        public class ServerSpringV4Config internal constructor(rootPackageName: String?) {
            public val packageName: String? = rootPackageName?.let { "$it.server" }
            public var postfix: String = "Api"
            public var addOperationContext: Boolean = false
            private var contextIfAnySecurity: ClassName? = null

            public fun contextIfAnySecurity(type: String): ServerSpringV4Config =
                apply { contextIfAnySecurity = type.toPoetClassName() }

            internal fun build() = de.quati.ogen.plugin.intern.model.config.GeneratorConfig.ServerSpringV4(
                packageName = packageName?.let(::PackageName) ?: error("packageName is required for server code"),
                postfix = postfix,
                addOperationContext = addOperationContext,
                contextIfAnySecurity = contextIfAnySecurity,
            )
        }

        public class SharedConfig internal constructor(rootPackageName: String?) {
            public var packageName: String? = rootPackageName?.let { "$it.shared" }
            public var generate: Boolean = true

            internal fun build() = de.quati.ogen.plugin.intern.model.config.SharedConfig(
                packageName = packageName?.let(::PackageName) ?: error("packageName is required for shared code"),
                generate = generate,
            )
        }

        public class ModelConfig internal constructor(rootPackageName: String?) {
            public var packageName: String? = rootPackageName?.let { "$it.model" }
            private val typeMappings = mutableMapOf<TypeWithFormat, Type.NonPrimitiveType.Custom>()
            private val schemaMappings = mutableMapOf<ComponentName.Schema, Type.NonPrimitiveType.Custom>()
            public var generate: Boolean = true
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

            internal fun build() = de.quati.ogen.plugin.intern.model.config.ModelConfig(
                packageName = packageName?.let(::PackageName) ?: error("packageName is required for model code"),
                generate = generate,
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