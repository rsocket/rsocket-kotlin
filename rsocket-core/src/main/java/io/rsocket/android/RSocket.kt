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

package io.rsocket

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import org.reactivestreams.Publisher

/**
 * A contract providing different interaction models for [RSocket protocol](https://github.com/RSocket/reactivesocket/blob/master/Protocol.md).
 */
interface RSocket : Availability, Closeable {

    /**
     * Fire and Forget interaction model of `RSocket`.
     *
     * @param payload Request payload.
     * @return `Publisher` that completes when the passed `payload` is successfully
     * handled, otherwise errors.
     */
    fun fireAndForget(payload: Payload): Completable

    /**
     * Request-Response interaction model of `RSocket`.
     *
     * @param payload Request payload.
     * @return `Publisher` containing at most a single `Payload` representing the
     * response.
     */
    fun requestResponse(payload: Payload): Single<Payload>

    /**
     * Request-Stream interaction model of `RSocket`.
     *
     * @param payload Request payload.
     * @return `Publisher` containing the stream of `Payload`s representing the response.
     */
    fun requestStream(payload: Payload): Flowable<Payload>

    /**
     * Request-Channel interaction model of `RSocket`.
     *
     * @param payloads Stream of request payloads.
     * @return Stream of response payloads.
     */
    fun requestChannel(payloads: Publisher<Payload>): Flowable<Payload>

    /**
     * Metadata-Push interaction model of `RSocket`.
     *
     * @param payload Request payloads.
     * @return `Publisher` that completes when the passed `payload` is successfully
     * handled, otherwise errors.
     */
    fun metadataPush(payload: Payload): Completable

    override fun availability(): Double {
        return 0.0
    }
}
