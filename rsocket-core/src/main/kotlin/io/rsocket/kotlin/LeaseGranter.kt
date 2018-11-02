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
     * @return [Completable] which signals error if underlying RSocket is closed,
     * completes successfully otherwise
     */
    fun grant(numberOfRequests: Int,
              ttlSeconds: Int): Completable

    /**
     * @return [Completable] which completes when RSocket is closed
     */
    fun onClose(): Completable
}