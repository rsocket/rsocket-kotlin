/*
 * Copyright 2015-2018 the original author or authors.
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

package io.rsocket.kotlin.internal.lease

/** A contract for RSocket lease, which is time bound.  */
internal interface Lease {

    /**
     * @return The number of requests allowed by this lease.
     */
    val allowedRequests: Int

    /**
     * @return Initial number of requests allowed by this lease.
     */
    val initialAllowedRequests: Int

    /**
     * @return Number of seconds that this lease is valid from the time
     * it is received.
     */
    val timeToLiveSeconds: Int

    /**
     * @return `true` if the lease has expired, false otherwise.
     */
    val isExpired: Boolean
        get() = isExpired(System.currentTimeMillis())

    /**
     * @return true if lease has not expired, and there are allowed requests
     * available, false otherwise
     */
    val isValid: Boolean

    /**
     * @return Absolute time since epoch at which this lease will expire.
     */
    val expiry: Long

    /**
     * @param now current time in millis.
     * @return `true` if the lease has expired for given `now`, false otherwise.
     */
    fun isExpired(now: Long): Boolean = now >= expiry
}
