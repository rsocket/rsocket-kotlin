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
