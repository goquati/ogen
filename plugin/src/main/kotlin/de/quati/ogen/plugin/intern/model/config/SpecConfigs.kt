package de.quati.ogen.plugin.intern.model.config

import de.quati.kotlin.util.poet.PackageName

internal data class SpecConfigs(
    val specs: List<SpecConfig>,
    val util: Util,
) {
    inline fun <reified T : GeneratorConfig> hasGeneratorConfig() =
        specs.any { it.generatorConfigs.any { c -> c is T } }


    data class Util(val packageName: PackageName) {
        val model = Model(
            packageName = packageName.plus("model"),
        )
        val serverSpringV4 = ServerSpringV4(
            packageName = packageName.plus("server.spring"),
        )

        data class Model(val packageName: PackageName)
        data class ServerSpringV4(val packageName: PackageName)
    }
}
