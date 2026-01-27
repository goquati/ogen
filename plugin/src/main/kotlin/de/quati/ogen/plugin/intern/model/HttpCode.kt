package de.quati.ogen.plugin.intern.model

internal sealed interface HttpCode {
    val isSuccess
        get() = when (this) {
            Success, Information -> true
            ClientError, Default, Redirection, ServerError, is Unknown -> false
            is Explicit -> code in 200..299
        }
    val defaultCode: Int

    object Information : HttpCode {
        override fun toString() = "1XX"
        override val defaultCode = 100
    }

    object Success : HttpCode {
        override fun toString() = "2XX"
        override val defaultCode = 200
    }

    object Redirection : HttpCode {
        override fun toString() = "3XX"
        override val defaultCode = 300
    }

    object ClientError : HttpCode {
        override fun toString() = "4XX"
        override val defaultCode = 400
    }

    object ServerError : HttpCode {
        override fun toString() = "5XX"
        override val defaultCode = 500
    }

    object Default : HttpCode {
        override fun toString() = "default"
        override val defaultCode = 200
    }

    data class Unknown(val code: String) : HttpCode {
        override fun toString() = code
        override val defaultCode = 200
    }

    data class Explicit(val code: Int) : HttpCode {
        override fun toString() = code.toString()
        override val defaultCode = code
    }

    companion object {
        fun parse(code: String) = when (code) {
            "default" -> Default
            "1XX" -> Information
            "2XX" -> Success
            "3XX" -> Redirection
            "4XX" -> ClientError
            "5XX" -> ServerError
            else -> code.toIntOrNull()?.let { Explicit(it) } ?: Unknown(code)
        }
    }
}
