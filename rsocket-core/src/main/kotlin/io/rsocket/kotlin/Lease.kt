/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rsocket.kotlin

import java.nio.ByteBuffer

/** A contract for RSocket lease, which is time bound.  */
interface Lease {

    /**
     * Number of requests allowed by this lease.
     *
     * @return The number of requests allowed by this lease.
     */
    val allowedRequests: Int

    /**
     * Number of seconds that this lease is valid from the time it is received.
     *
     * @return Number of seconds that this lease is valid from the time it is received.
     */
    val ttl: Int

    /**
     * Metadata for the lease.
     *
     * @return Metadata for the lease.
     */
    val metadata: ByteBuffer

    /**
     * Checks if the lease is expired now.
     *
     * @return `true` if the lease has expired.
     */
    val isExpired: Boolean
        get() = isExpired(System.currentTimeMillis())

    /** Checks if the lease has not expired and there are allowed requests available  */
    val isValid: Boolean
        get() = !isExpired && allowedRequests > 0

    /**
     * Absolute time since epoch at which this lease will expire.
     *
     * @return Absolute time since epoch at which this lease will expire.
     */
    fun expiry(): Long

    /**
     * Checks if the lease is expired for the passed `now`.
     *
     * @param now current time in millis.
     * @return `true` if the lease has expired.
     */
    fun isExpired(now: Long): Boolean = now >= expiry()
}
