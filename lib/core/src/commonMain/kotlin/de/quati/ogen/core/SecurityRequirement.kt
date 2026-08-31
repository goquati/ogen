package de.quati.ogen.core

import kotlin.jvm.JvmInline

@JvmInline
public value class SecurityRequirement(
    public val requirements: List<SecurityRequirementObject>,
)
