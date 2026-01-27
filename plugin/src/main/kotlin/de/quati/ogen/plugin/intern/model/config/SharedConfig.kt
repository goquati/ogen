package de.quati.ogen.plugin.intern.model.config

import de.quati.kotlin.util.poet.PackageName


internal data class SharedConfig(
    val packageName: PackageName,
    val generate: Boolean,
)