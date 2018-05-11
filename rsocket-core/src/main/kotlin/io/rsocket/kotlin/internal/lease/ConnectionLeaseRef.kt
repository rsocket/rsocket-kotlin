/*
 * Copyright 2018 Maksym Ostroverkhov
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.rsocket.kotlin.internal.lease

import io.reactivex.Completable
import io.rsocket.kotlin.LeaseRef
import java.nio.ByteBuffer

internal class ConnectionLeaseRef(private val leaseGranterConnection
                                  : LeaseGranterConnection) : LeaseRef {

    override fun grantLease(numberOfRequests: Int,
                            ttlMillis: Long,
                            metadata: ByteBuffer): Completable {
        return grant(
                numberOfRequests,
                ttlMillis,
                metadata)
    }

    override fun grantLease(numberOfRequests: Int,
                            timeToLiveMillis: Long): Completable {
        return grant(
                numberOfRequests,
                timeToLiveMillis,
                null)
    }

    override fun onClose(): Completable = leaseGranterConnection.onClose()

    private fun grant(
            numberOfRequests: Int,
            ttlMillis: Long,
            metadata: ByteBuffer?): Completable {
        assertArgs(numberOfRequests, ttlMillis)
        val ttl = Math.toIntExact(ttlMillis)
        return leaseGranterConnection.grantLease(
                numberOfRequests,
                ttl,
                metadata)
    }

    private fun assertArgs(numberOfRequests: Int, ttl: Long) {
        if (numberOfRequests <= 0) {
            throw IllegalArgumentException(
                    "numberOfRequests must be non-negative: $numberOfRequests")
        }
        if (ttl <= 0) {
            throw IllegalArgumentException(
                    "timeToLive must be non-negative: $ttl")
        }
    }
}
