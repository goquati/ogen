package de.quati.ogen

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@JvmInline
@Serializable
value class UserId(val value: Uuid) {
    constructor(value: String) : this(Uuid.parse(value))
}