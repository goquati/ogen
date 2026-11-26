package de.quati.ogen.core.validation

import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CustomConstraintsTest {
    private fun multipleOf(divisor: String) =
        MultipleOfValidator().apply { initialize(MultipleOf(value = divisor)) }

    @Test
    fun `multipleOf of integers`() {
        val validator = multipleOf("5")
        listOf(0, 15, -10, 15L, BigInteger("100000000000000000000"), null).forEach {
            assertTrue(validator.isValid(it, null), "$it")
        }
        listOf(12, 3L, BigInteger("100000000000000000001")).forEach { assertFalse(validator.isValid(it, null), "$it") }
    }

    @Test
    fun `multipleOf of decimals is exact`() {
        // with floating-point arithmetic, 0.3 % 0.1 is not 0
        val validator = multipleOf("0.1")
        listOf(0.3, 0.3f, BigDecimal("12.7"), 5).forEach { assertTrue(validator.isValid(it, null), "$it") }
        listOf(0.35, BigDecimal("0.01"), Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertFalse(validator.isValid(it, null), "$it")
        }
    }

    @Test
    fun uniqueItems() {
        val validator = UniqueItemsValidator()
        listOf(null, emptyList(), listOf("a", "b"), listOf(listOf(1), listOf(2))).forEach {
            assertTrue(validator.isValid(it, null), "$it")
        }
        listOf(listOf("a", "a"), listOf(listOf(1), listOf(1)), listOf(null, null)).forEach {
            assertFalse(validator.isValid(it, null), "$it")
        }
    }
}
