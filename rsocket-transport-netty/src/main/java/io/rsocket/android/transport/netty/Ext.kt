package io.rsocket.android.transport.netty

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

internal fun <T> Flux<T>.toFlowable(): Flowable<T> = Flowable.fromPublisher(this)

internal fun Mono<Void>.toCompletable(): Completable = Completable.fromPublisher(this)

internal fun <T> Mono<T>.toSingle(): Single<T> = Single.fromPublisher(this)

internal fun Completable.toMono(): Mono<Void> = Mono.from(toFlowable())