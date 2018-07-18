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

package io.rsocket.kotlin.test.util

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.processors.FlowableProcessor
import io.reactivex.processors.PublishProcessor
import io.rsocket.kotlin.DuplexConnection
import io.rsocket.kotlin.Frame
import org.reactivestreams.Publisher

class LocalDuplexConnection(
        private val name: String,
        private val send: FlowableProcessor<Frame>,
        private val receive: FlowableProcessor<Frame>) : DuplexConnection {

    private val closeNotifier: PublishProcessor<Void> = PublishProcessor.create()
    private var availability = 1.0

    override fun send(frame: Publisher<Frame>): Completable {
        return Flowable.fromPublisher(frame)
                .doOnNext { f -> println(name + " - " + f.toString()) }
                .doOnNext({ send.onNext(it) })
                .doOnError({ send.onError(it) })
                .ignoreElements()
    }

    override fun receive(): Flowable<Frame> = receive.doOnNext { f -> println(name + " - " + f.toString()) }

    override fun availability(): Double = availability

    override fun close(): Completable {
        return Completable.defer {
            closeNotifier.onComplete()
            Completable.complete()
        }
    }

    override fun onClose(): Completable = closeNotifier.ignoreElements()

    fun setAvailability(availability: Double) {
        this.availability = availability
    }
}

