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
        val clientKtor = ClientKtor(
            packageName = packageName.plus("client.ktor"),
        )
        val securityRequirementObject get() = packageName.className("SecurityRequirementObject")
        val securityRequirement get() = packageName.className("SecurityRequirement")

        val optionSerializer = packageName.className("OptionSerializer")
        val valueSerializer = packageName.className("ValueSerializer")
        val operationContext = packageName.className("OperationContext")

        data class Model(val packageName: PackageName)
        data class ServerSpringV4(val packageName: PackageName)
        data class ClientKtor(val packageName: PackageName) {
            val httpResponseTyped get() = packageName.className("HttpResponseTyped")
            val httpClientOgen get() = packageName.className("HttpClientOgen")
            val toTyped get() = (packageName + "HttpResponseTyped" + "Companion").className("toTyped")
            val ogenAuthAttr get() = packageName.className("ogenAuthAttr")
        }
    }
}
