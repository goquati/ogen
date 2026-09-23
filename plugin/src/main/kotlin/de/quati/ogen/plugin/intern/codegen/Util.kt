package de.quati.ogen.plugin.intern.codegen

import java.util.Locale.getDefault

internal fun String.oGenCapitalize(): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() }
