package io.rsocket.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RSocketCustomErrorTest {

    @Test
    fun shouldSuccessfullyCreate() = assertSuccessfulCreation(0x00000301)

    @Test
    fun shouldSuccessfullyCreateAtLowerBond() = assertSuccessfulCreation(0x00000301)

    @Test
    fun shouldSuccessfullyCreateNearLowerBond() = assertSuccessfulCreation(0x00000555)

    @Test
    fun shouldSuccessfullyCreateAtUpperBond() = assertSuccessfulCreation(0xFFFFFFFE.toInt())

    @Test
    fun shouldSuccessfullyCreateNearUpperBond() = assertSuccessfulCreation(0xFFFFFFAE.toInt())

    @Test
    fun shouldFailBelowLowerBond() = assertFailureCreation(0x00000300)

    @Test
    fun shouldFailOverUpperBond() = assertFailureCreation(0xFFFFFFFF.toInt())

    @Test
    fun shouldSuccessfullyCreateWithCustomCode() = assertSuccessfulCreationWithCustomCode(0x42)

    @Test
    fun shouldSuccessfullyCreateWithLargeCustomCode() = assertSuccessfulCreationWithCustomCode(0xFFFFFAFF)

    @Test
    fun shouldSuccessfullyCreateCustomCodeAtLowerBond() = assertSuccessfulCreationWithCustomCode(0x0)

    @Test
    fun shouldFailIfCustomCodeBelowLowerBond() = assertFailureCreationWithCustomCode(-1)

    @Test
    fun shouldSuccessfullyCreateCustomCodeAtUpperBond() = assertSuccessfulCreationWithCustomCode(0xFFFFFCFC)

    @Test
    fun shouldFailIfCreateCustomCodeOverUpperBond() = assertFailureCreationWithCustomCode(0xFFFFFCFD)

    private fun assertSuccessfulCreation(errorCode: Int) {
        val error = RSocketError.Custom(errorCode, "custom")
        assertEquals(errorCode, error.errorCode)
    }

    private fun assertSuccessfulCreationWithCustomCode(customCode: Long) {
        val error = RSocketError.Custom.fromCustomCode(customCode, "custom")
        assertEquals(customCode, error.customCode)
    }

    private fun assertFailureCreation(errorCode: Int) {
        assertFailsWith(IllegalArgumentException::class) {
            RSocketError.Custom(errorCode, "custom")
        }
    }

    private fun assertFailureCreationWithCustomCode(customCode: Long) {
        assertFailsWith(IllegalArgumentException::class) {
            RSocketError.Custom.fromCustomCode(customCode, "custom")
        }
    }
}