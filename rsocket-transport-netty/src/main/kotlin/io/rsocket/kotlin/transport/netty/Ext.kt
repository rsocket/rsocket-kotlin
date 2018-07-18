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

package io.rsocket.kotlin.transport.netty

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

internal fun <T> Flux<T>.toFlowable(): Flowable<T> = Flowable.fromPublisher(this)

internal fun Mono<Void>.toCompletable(): Completable = Completable.fromPublisher(this)

internal fun <T> Mono<T>.toSingle(): Single<T> = Single.fromPublisher(this)

internal fun Completable.toMono(): Mono<Void> = Mono.from(toFlowable())

internal const val frameLengthSize = 3

internal const val frameLengthMask = 0xFFFFFF