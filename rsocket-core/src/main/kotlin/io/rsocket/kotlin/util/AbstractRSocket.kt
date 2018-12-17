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

package io.rsocket.kotlin.util

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.processors.AsyncProcessor
import io.rsocket.kotlin.Payload
import io.rsocket.kotlin.RSocket
import org.reactivestreams.Publisher

/**
 * An abstract implementation of [RSocket]. All request handling methods emit error
 * and hence must be overridden to provide a valid implementation.
 *
 */
abstract class AbstractRSocket : RSocket {

    private val onClose: AsyncProcessor<Void> = AsyncProcessor.create()

    override fun fireAndForget(payload: Payload): Completable =
            Completable.error(UnsupportedOperationException(
                    "Fire and forget not implemented."))

    override fun requestResponse(payload: Payload): Single<Payload> =
            Single.error(UnsupportedOperationException(
                    "Request-Response not implemented."))

    override fun requestStream(payload: Payload): Flowable<Payload> =
            Flowable.error(UnsupportedOperationException(
                    "Request-Stream not implemented."))

    override fun requestChannel(payloads: Publisher<Payload>): Flowable<Payload> =
            Flowable.error(UnsupportedOperationException(
                    "Request-Channel not implemented."))

    override fun metadataPush(payload: Payload): Completable =
            Completable.error(UnsupportedOperationException(
                    "Metadata-Push not implemented."))

    override fun close(): Completable {
        return Completable.defer {
            onClose.onComplete()
            onComplete()
        }
    }

    override fun onClose(): Completable = onComplete()

    private fun onComplete() = onClose.ignoreElements()
}
