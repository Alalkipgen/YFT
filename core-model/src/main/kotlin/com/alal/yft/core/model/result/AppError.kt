package com.alal.yft.core.model.result

data class AppError(
    val code: Code,
    val userMessage: String,
    val retryable: Boolean = false,
) {
    enum class Code {
        INVALID_INPUT,
        NETWORK,
        STORAGE,
        DATABASE,
        UNSUPPORTED_MEDIA,
        UNKNOWN,
    }
}
