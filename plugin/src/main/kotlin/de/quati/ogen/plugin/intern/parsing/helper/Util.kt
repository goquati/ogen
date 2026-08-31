package de.quati.ogen.plugin.intern.parsing.helper

internal fun longestCommonPrefix(a: String, b: String): String {
    val n = minOf(a.length, b.length)
    var i = 0
    while (i < n && a[i] == b[i]) i++
    return a.substring(0, i)
}