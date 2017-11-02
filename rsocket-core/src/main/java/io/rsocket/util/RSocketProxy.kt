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
package io.rsocket.util

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.rsocket.Payload
import io.rsocket.RSocket
import org.reactivestreams.Publisher

/** Wrapper/Proxy for a RSocket. This is useful when we want to override a specific method.  */
open class RSocketProxy(protected val source: RSocket) : RSocket {

    override fun fireAndForget(payload: Payload): Completable {
        return source.fireAndForget(payload)
    }

    override fun requestResponse(payload: Payload): Single<Payload> {
        return source.requestResponse(payload)
    }

    override fun requestStream(payload: Payload): Flowable<Payload> {
        return source.requestStream(payload)
    }

    override fun requestChannel(payloads: Publisher<Payload>): Flowable<Payload> {
        return source.requestChannel(payloads)
    }

    override fun metadataPush(payload: Payload): Completable {
        return source.metadataPush(payload)
    }

    override fun availability(): Double {
        return source.availability()
    }

    override fun close(): Completable {
        return source.close()
    }

    override fun onClose(): Completable {
        return source.onClose()
    }
}
