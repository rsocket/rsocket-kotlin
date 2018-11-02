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

import io.netty.buffer.Unpooled
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.processors.PublishProcessor
import io.rsocket.kotlin.DuplexConnection
import io.rsocket.kotlin.Frame
import io.rsocket.kotlin.FrameType
import io.rsocket.kotlin.test.util.LocalDuplexConnection
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.util.concurrent.TimeUnit

class LeaseConnectionTest {

    private lateinit var leaseGranterConnection: LeaseConnection
    private lateinit var sender: PublishProcessor<Frame>
    private lateinit var receiver: PublishProcessor<Frame>
    private lateinit var send: LeaseManager
    private lateinit var receive: LeaseManager
    private lateinit var conn: DuplexConnection

    @Before
    fun setUp() {
        val leaseContext = LeaseContext()
        send = LeaseManager("send")
        receive = LeaseManager("receive")
        sender = PublishProcessor.create()
        receiver = PublishProcessor.create()
        conn = LocalDuplexConnection("test", sender, receiver)
        leaseGranterConnection = LeaseConnection(
                leaseContext,
                conn,
                send,
                receive)
    }

    @Test
    fun sentLease() {
        leaseGranterConnection.send(
                Flowable.just(Frame.Lease.from(2_000, 1, Unpooled.EMPTY_BUFFER)))
                .blockingAwait()
        assertEquals(1.0, receive.availability(), 1e-5)
        assertEquals(0.0, send.availability(), 1e-5)
    }

    @Test
    fun receivedLease() {
        leaseGranterConnection.receive().subscribe()
        receiver.onNext(Frame.Lease.from(2, 1, Unpooled.EMPTY_BUFFER))
        assertEquals(0.0, receive.availability(), 1e-5)
        assertEquals(1.0, send.availability(), 1e-5)
    }

    @Test
    fun grantLease() {
        Completable.timer(100, TimeUnit.MILLISECONDS)
                .andThen(Completable.defer {
                    leaseGranterConnection
                            .grant(2, 1)
                })
                .subscribe()
        val f = sender.firstOrError().blockingGet()

        assertNotNull(f)
        assertTrue(f.type === FrameType.LEASE)
        assertEquals(2, Frame.Lease.numberOfRequests(f))
        assertEquals(1, Frame.Lease.ttl(f))
    }

    @Test
    fun grantLeaseAfterClose() {
        leaseGranterConnection.onClose().subscribe()
        conn.close().blockingAwait(5, TimeUnit.SECONDS)
        leaseGranterConnection.grant(2,1)
                .test()
                .assertError(ClosedChannelException::class.java)
    }
}
