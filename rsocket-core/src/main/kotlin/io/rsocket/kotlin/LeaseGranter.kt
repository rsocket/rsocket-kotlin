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

package io.rsocket.kotlin

import io.reactivex.Completable
import java.nio.ByteBuffer

/**
 * Grants Lease to its peer
 */
interface LeaseGranter {
    /**
     * Grants lease to its peer
     *
     * @param numberOfRequests number of requests peer is allowed to perform.
     *                         Must be positive
     * @param ttlSeconds number of seconds that this lease is valid from the time
     * it is received.
     * @param metadata metadata associated with this lease grant
     * @return [Completable] which completes once Lease is sent
     */
    fun grantLease(
            numberOfRequests: Int,
            ttlSeconds: Int,
            metadata: ByteBuffer): Completable

    /**
     * Grants lease to its peer
     *
     * @param numberOfRequests number of requests peer is allowed to perform.
     *                         Must be positive
     * @param ttlSeconds number of seconds that this lease is valid from the time
     * it is received.
     * @return [Completable] which completes once Lease is sent
     */
    fun grantLease(numberOfRequests: Int,
                   ttlSeconds: Int): Completable

    /**
     * @return [Completable] which completes when associated RSocket is closed
     */
    fun onClose(): Completable
}