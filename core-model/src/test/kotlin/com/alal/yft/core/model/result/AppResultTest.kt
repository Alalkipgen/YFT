package com.alal.yft.core.model.result

import org.junit.Assert.assertEquals
import org.junit.Test

class AppResultTest {
    @Test
    fun mapsSuccessValue() {
        val result = AppResult.Success(4).map { it * 2 }

        assertEquals(AppResult.Success(8), result)
    }

    @Test
    fun preservesFailure() {
        val error = AppError(AppError.Code.NETWORK, "Network unavailable", retryable = true)
        val result = AppResult.Failure(error).map { value: Int -> value * 2 }

        assertEquals(AppResult.Failure(error), result)
    }
}
