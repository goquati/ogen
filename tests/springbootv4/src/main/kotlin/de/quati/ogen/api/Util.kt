package de.quati.ogen.api

import org.springframework.http.ResponseEntity

fun ResponseEntity.BodyBuilder.addInputHeader(vararg args: Any?) {
    header("input-data", args.joinToString("|"))
}