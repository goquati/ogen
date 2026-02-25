package de.quati.ogen.plugin.intern.codegen.util

import de.quati.ogen.plugin.intern.codegen.GlobalGenContext
import de.quati.ogen.plugin.intern.model.config.GeneratorConfig
import de.quati.ogen.plugin.intern.tasks.Generator

context(c: GlobalGenContext)
internal fun Generator.syncModelUtils() {
    if (!c.specConfigs.hasGeneratorConfig<GeneratorConfig.Model>()) return
    directorySync(packageName = c.utilConfig.model.packageName) {
        sync(fileName = "Serializer.kt") {
            addType(c.buildSerializerTypeSpec())
        }
    }
}
