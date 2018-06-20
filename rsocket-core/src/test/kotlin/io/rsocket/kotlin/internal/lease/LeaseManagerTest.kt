package io.rsocket.kotlin.internal.lease

import io.reactivex.Completable
import io.rsocket.kotlin.exceptions.MissingLeaseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

class LeaseManagerTest {

    private lateinit var leaseManager: LeaseManager

    @Before
    fun setUp() {
        leaseManager = LeaseManager("")
    }

    @Test
    fun initialLeaseAvailability() {
        assertEquals(0.0, leaseManager.availability(), 1e-5)
    }

    @Test
    fun useNoRequests() {
        val result = leaseManager.use()
        assertTrue(result is Error)
        result as Error
        assertTrue(result.ex is MissingLeaseException)
    }

    @Test
    fun grant() {
        leaseManager.grant(2, 1_000, EMPTY_DATA)
        assertEquals(1.0, leaseManager.availability(), 1e-5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun grantLeaseZeroRequests() {
        leaseManager.grant(0, 1_000, EMPTY_DATA)
    }

    @Test(expected = IllegalArgumentException::class)
    fun grantLeaseZeroTtl() {
        leaseManager.grant(1, 0, EMPTY_DATA)
    }

    @Test
    fun use() {
        leaseManager.grant(2, 1_000, EMPTY_DATA)
        leaseManager.use()
        assertEquals(0.5, leaseManager.availability(), 1e-5)
    }

    @Test
    fun useTimeout() {
        leaseManager.grant(2, 1_000, EMPTY_DATA)
        Completable.timer(1500, TimeUnit.MILLISECONDS).blockingAwait()
        val result = leaseManager.use()
        assertTrue(result is Error)
        result as Error
        assertTrue(result.ex is MissingLeaseException)
    }

    companion object {
        private val EMPTY_DATA = ByteBuffer.allocateDirect(0)
    }
}
