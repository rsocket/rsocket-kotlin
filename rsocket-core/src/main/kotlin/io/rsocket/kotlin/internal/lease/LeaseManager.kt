/*
 * Copyright 2015-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rsocket.kotlin.internal.lease

import io.rsocket.kotlin.Lease
import io.rsocket.kotlin.exceptions.MissingLeaseException
import java.nio.ByteBuffer

/**
 * Updates Lease on use and grant
 */
internal class LeaseManager(private val tag: String) {
    @Volatile
    private var currentLease = INVALID_MUTABLE_LEASE

    init {
        requireNotNull(tag, { "tag" })
    }

    fun availability(): Double = currentLease.availability()

    fun grant(numberOfRequests: Int, ttl: Int, metadata: ByteBuffer): Lease {
        assertGrantedLease(numberOfRequests, ttl)
        currentLease = LeaseImpl(numberOfRequests, ttl, metadata)
        return hide(currentLease)
    }

    fun use(): Result =
            if (currentLease.use(1))
                Success
            else
                Error(MissingLeaseException(currentLease, tag))

    private fun hide(l: Lease): Lease = DelegatingLease(l)

    private class DelegatingLease(l: Lease) : Lease by l

    override fun toString(): String {
        return "LeaseManager{tag='$tag'}"
    }

    companion object {
        private val INVALID_MUTABLE_LEASE = LeaseImpl.invalidLease()

        private fun assertGrantedLease(numberOfRequests: Int, ttl: Int) {
            if (numberOfRequests <= 0) {
                throw IllegalArgumentException("numberOfRequests must be positive")
            }
            if (ttl <= 0) {
                throw IllegalArgumentException("time-to-live must be positive")
            }
        }
    }
}

internal sealed class Result

internal object Success : Result()

internal data class Error(val ex: Throwable) : Result()