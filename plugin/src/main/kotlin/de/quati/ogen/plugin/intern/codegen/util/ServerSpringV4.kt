package de.quati.ogen.plugin.intern.codegen.util

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import de.quati.kotlin.util.poet.dsl.addClass
import de.quati.kotlin.util.poet.dsl.addCode
import de.quati.kotlin.util.poet.dsl.addFunction
import de.quati.ogen.plugin.intern.codegen.GlobalGenContext
import de.quati.ogen.plugin.intern.codegen.Poet
import de.quati.ogen.plugin.intern.model.Type
import de.quati.ogen.plugin.intern.model.config.GeneratorConfig
import de.quati.ogen.plugin.intern.tasks.Generator

context(c: GlobalGenContext)
internal fun Generator.syncServerSpringV4Utils() {
    if (!c.specConfigs.hasGeneratorConfig<GeneratorConfig.ServerSpringV4>()) return
    directorySync(packageName = c.utilConfig.serverSpringV4.packageName) {
        sync(fileName = "OgenWebFluxConversionConfig.kt") {
            addWebFluxConversionConfig()
        }
    }
}

context(c: GlobalGenContext)
private fun FileSpec.Builder.addWebFluxConversionConfig() = addClass("OgenWebFluxConversionConfig") {
    addAnnotation(Poet.Spring.configuration)
    addSuperinterface(Poet.Spring.WebFlux.webFluxConfigurer)
    addFunction("addFormatters") {
        addModifiers(KModifier.OVERRIDE)
        addParameter("reg", Poet.Spring.formatterRegistry)
        addCode {
            run {
                val type = Type.PrimitiveType.Uuid.poet
                add("reg.addConverter(String::class.java, %T::class.java) { %T.parse(it) }\n", type, type)
            }
            c.serverSpringV4EnumConversionTypes.forEach { type ->
                val typeName = type.poet
                add("reg.addConverter(String::class.java, %T::class.java) { %T.fromSerial(it) }\n", typeName, typeName)
            }
        }
    }
}
