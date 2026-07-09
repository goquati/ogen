package de.quati.ogen.plugin.intern.model

internal sealed interface HttpCode {
    val isSuccess
        get() = when (this) {
            Success, Information -> true
            ClientError, Default, Redirection, ServerError, is Unknown -> false
            is Explicit -> code in 200..299
        }
    val value: String
    val defaultCode: Int

    object Information : HttpCode {
        override val value = "1XX"
        override val defaultCode = 100
        override fun toString() = value
    }

    object Success : HttpCode {
        override val value = "2XX"
        override val defaultCode = 200
        override fun toString() = value
    }

    object Redirection : HttpCode {
        override val value = "3XX"
        override val defaultCode = 300
        override fun toString() = value
    }

    object ClientError : HttpCode {
        override val value = "4XX"
        override val defaultCode = 400
        override fun toString() = value
    }

    object ServerError : HttpCode {
        override val value = "5XX"
        override val defaultCode = 500
        override fun toString() = value
    }

    object Default : HttpCode {
        override val value = "default"
        override val defaultCode = 200
        override fun toString() = value
    }

    data class Unknown(override val value: String) : HttpCode {
        override val defaultCode = value.toIntOrNull() ?: 200
        override fun toString() = value
    }

    data class Explicit(val code: Int) : HttpCode {
        override val value = code.toString()
        override val defaultCode = code
        override fun toString() = value
    }

    companion object {
        private val staticCodes = listOf(Default, Information, Success, Redirection, ClientError, ServerError)
        fun parse(code: String): HttpCode = staticCodes
            .firstOrNull { code.equals(it.value, ignoreCase = true) }
            ?: code.toIntOrNull()?.let(::Explicit)
            ?: Unknown(code)
    }
}
