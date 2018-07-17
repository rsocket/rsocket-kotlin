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

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.rsocket.kotlin.Payload
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.util.RSocketProxy
import org.reactivestreams.Publisher

internal class LeaseRSocket(
        private val leaseContext: LeaseContext,
        source: RSocket, private
        val tag: String,
        private val leaseManager: LeaseManager) : RSocketProxy(source) {

    override fun fireAndForget(payload: Payload): Completable =
            request(super.fireAndForget(payload))

    override fun requestResponse(payload: Payload): Single<Payload> =
            request(super.requestResponse(payload))

    override fun requestStream(payload: Payload): Flowable<Payload> =
            request(super.requestStream(payload))

    override fun requestChannel(payloads: Publisher<Payload>): Flowable<Payload> =
            request(super.requestChannel(payloads))

    override fun availability(): Double =
            Math.min(super.availability(), leaseManager.availability())

    override fun toString(): String =
            "LeaseRSocket(leaseContext=$leaseContext," +
                    " tag='$tag', " +
                    "leaseManager=$leaseManager)"

    private fun request(actual: Completable): Completable =
            request(
                    { Completable.defer(it) },
                    actual,
                    { Completable.error(it) })

    private fun <K> request(actual: Single<K>): Single<K> =
            request(
                    { Single.defer(it) },
                    actual,
                    { Single.error(it) })

    private fun <K> request(actual: Flowable<K>): Flowable<K> =
            request(
                    { Flowable.defer(it) },
                    actual,
                    { Flowable.error(it) })

    private fun <T> request(
            defer: (() -> T) -> T,
            actual: T,
            error: (Throwable) -> T): T =
            defer {
                if (isEnabled()) {
                    val result = leaseManager.use()
                    when (result) {
                        is Success -> actual
                        is Error -> error(result.ex)
                    }
                } else {
                    actual
                }
            }

    private fun isEnabled() = leaseContext.leaseEnabled
}
