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
 *//*


package io.rsocket.test.util

import io.rsocket.DuplexConnection
import io.rsocket.Frame
import org.reactivestreams.Publisher
import reactor.core.publisher.DirectProcessor
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.MonoProcessor

class LocalDuplexConnection(
    private val name: String, private val send: DirectProcessor<Frame>, private val receive: DirectProcessor<Frame>) : DuplexConnection {
    private val closeNotifier: MonoProcessor<Void> = MonoProcessor.create()

    override fun send(frame: Publisher<Frame>): Mono<Void> {
        return Flux.from(frame)
                .doOnNext { f -> println(name + " - " + f.toString()) }
                .doOnNext({ send.onNext(it) })
                .doOnError({ send.onError(it) })
                .then()
    }

    override fun receive(): Flux<Frame> {
        return receive.doOnNext { f -> println(name + " - " + f.toString()) }
    }

    override fun availability(): Double {
        return 1.0
    }

    override fun close(): Mono<Void> {
        return Mono.defer {
            closeNotifier.onComplete()
            Mono.empty<Void>()
        }
    }

    override fun onClose(): Mono<Void> {
        return closeNotifier
    }
}
*/
