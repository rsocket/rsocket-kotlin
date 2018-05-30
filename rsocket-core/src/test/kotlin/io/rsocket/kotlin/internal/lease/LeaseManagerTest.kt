package io.rsocket.kotlin.internal.lease

import io.reactivex.Completable
import io.rsocket.kotlin.exceptions.MissingLeaseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
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
        val result = leaseManager.useLease()
        assertTrue(result is Error)
        result as Error
        assertTrue(result.ex is MissingLeaseException)
    }

    @Test
    fun grant() {
        leaseManager.grantLease(2, 1_000)
        assertEquals(1.0, leaseManager.availability(), 1e-5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun grantLeaseZeroRequests() {
        leaseManager.grantLease(0, 1_000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun grantLeaseZeroTtl() {
        leaseManager.grantLease(1, 0)
    }

    @Test
    fun use() {
        leaseManager.grantLease(2, 1_000)
        leaseManager.useLease()
        assertEquals(0.5, leaseManager.availability(), 1e-5)
    }

    @Test
    fun useTimeout() {
        leaseManager.grantLease(2, 1_000)
        Completable.timer(1500, TimeUnit.MILLISECONDS).blockingAwait()
        val result = leaseManager.useLease()
        assertTrue(result is Error)
        result as Error
        assertTrue(result.ex is MissingLeaseException)
    }
}
