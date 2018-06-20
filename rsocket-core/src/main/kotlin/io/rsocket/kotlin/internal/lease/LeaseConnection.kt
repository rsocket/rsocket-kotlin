package io.rsocket.kotlin.internal.lease

import io.netty.buffer.Unpooled
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.processors.BehaviorProcessor
import io.rsocket.kotlin.*
import io.rsocket.kotlin.util.DuplexConnectionProxy
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import java.nio.ByteBuffer

internal class LeaseConnection(
        private val leaseContext: LeaseContext,
        source: DuplexConnection,
        private val sendManager: LeaseManager,
        private val receiveManager: LeaseManager)
    : DuplexConnectionProxy(source), LeaseGranter, LeaseWatcher {

    private val sentLease = BehaviorProcessor.create<Lease>()
    private val receivedLease = BehaviorProcessor.create<Lease>()

    override fun grantLease(numberOfRequests: Int,
                            ttlSeconds: Int,
                            metadata: ByteBuffer): Completable =
            sendLease(numberOfRequests, ttlSeconds, metadata)

    override fun grantLease(numberOfRequests: Int,
                            ttlSeconds: Int): Completable =
            sendLease(numberOfRequests, ttlSeconds, null)

    override fun received(): Flowable<Lease> = receivedLease

    override fun granted(): Flowable<Lease> = sentLease

    override fun send(frame: Publisher<Frame>): Completable {
        return super.send(Flowable.fromPublisher(frame)
                .doOnNext { f ->
                    leaseGrantedTo(
                            f,
                            receiveManager,
                            sentLease)
                })
    }

    override fun receive(): Flowable<Frame> {
        return super.receive()
                .doOnNext { f ->
                    leaseGrantedTo(
                            f,
                            sendManager,
                            receivedLease)
                }
    }

    private fun leaseGrantedTo(f: Frame,
                               leaseManager: LeaseManager,
                               leaseReceiver: Subscriber<Lease>) {
        if (isEnabled() && isLease(f)) {
            val requests = Frame.Lease.numberOfRequests(f)
            val ttl = Frame.Lease.ttl(f)
            val metadata = f.metadata
            val lease = leaseManager.grant(requests, ttl, metadata)
            leaseReceiver.onNext(lease)
        }
    }

    private fun sendLease(requests: Int,
                          ttl: Int,
                          metadata: ByteBuffer?): Completable {

        val byteBuf = if (metadata == null) Unpooled.EMPTY_BUFFER
        else Unpooled.wrappedBuffer(metadata)

        return when {
            ttl <= 0 -> return Completable
                    .error(IllegalArgumentException(
                            "Time-to-live should be positive"))
            requests <= 0 -> Completable
                    .error(IllegalArgumentException(
                            "Allowed requests should be positive"))
            else -> send(Flowable.just(Frame.Lease.from(ttl, requests, byteBuf)))
        }
    }

    private fun isLease(f: Frame) = f.type === FrameType.LEASE

    private fun isEnabled() = leaseContext.leaseEnabled
}
