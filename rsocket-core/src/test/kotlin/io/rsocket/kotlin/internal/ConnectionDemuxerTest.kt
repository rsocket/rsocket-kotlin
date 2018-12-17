/*
 * Copyright 2015-2018 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.rsocket.kotlin.internal

import io.netty.buffer.Unpooled.EMPTY_BUFFER
import io.rsocket.kotlin.DuplexConnection
import org.junit.Assert.assertEquals

import io.rsocket.kotlin.Frame
import io.rsocket.kotlin.FrameType
import io.rsocket.kotlin.internal.frame.SetupFrameFlyweight
import io.rsocket.kotlin.test.util.TestDuplexConnection
import io.rsocket.kotlin.DefaultPayload
import org.junit.Before
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Test

internal abstract class ConnectionDemuxerTest {
    lateinit var source: TestDuplexConnection
    lateinit var demuxer: ConnectionDemuxer

    lateinit var requesterFrames: AtomicInteger
    lateinit var responderFrames: AtomicInteger
    lateinit var setupFrames: AtomicInteger
    lateinit var serviceFrames: AtomicInteger

    @Before
    fun setUp() {
        source = TestDuplexConnection()
        requesterFrames = AtomicInteger()
        responderFrames = AtomicInteger()
        setupFrames = AtomicInteger()
        serviceFrames = AtomicInteger()
        demuxer = createDemuxer(source, InterceptorRegistry())

        demuxer
                .requesterConnection()
                .receive()
                .subscribe { requesterFrames.incrementAndGet() }

        demuxer
                .responderConnection()
                .receive()
                .subscribe { responderFrames.incrementAndGet() }

        demuxer
                .setupConnection()
                .receive()
                .subscribe { setupFrames.incrementAndGet() }

        demuxer
                .serviceConnection()
                .receive()
                .subscribe { serviceFrames.incrementAndGet() }
    }

    @Test
    fun metadata() {
        val metadata = Frame.Request.from(
                0,
                FrameType.METADATA_PUSH,
                DefaultPayload.text("", "md"),
                1)

        source.addToReceivedBuffer(metadata)

        assertEquals(1, responderFrames.get())
        assertEquals(0, requesterFrames.get())
        assertEquals(0, setupFrames.get())
        assertEquals(0, serviceFrames.get())
    }

    @Test
    fun service() {
        val error = Frame.Error.from(0, Exception())
        val lease = Frame.Lease.from(10_000, 42, EMPTY_BUFFER)
        val keepAlive = Frame.Keepalive.from(EMPTY_BUFFER, true)
        val frames = arrayListOf(error, lease, keepAlive)
        var i = 1
        for (frame in frames) {
            source.addToReceivedBuffer(frame)
            assertEquals(0, responderFrames.get())
            assertEquals(0, requesterFrames.get())
            assertEquals(0, setupFrames.get())
            assertEquals(i++, serviceFrames.get())
        }
    }

    @Test
    fun setup() {
        val setup = Frame.Setup.from(0,
                SetupFrameFlyweight.CURRENT_VERSION,
                0, 0,
                "test",
                "test",
                DefaultPayload.EMPTY)
        source.addToReceivedBuffer(setup)
        assertEquals(0, responderFrames.get())
        assertEquals(0, requesterFrames.get())
        assertEquals(1, setupFrames.get())
        assertEquals(0, serviceFrames.get())
    }

    abstract fun requester()

    abstract fun responder()

    abstract fun createDemuxer(conn: DuplexConnection,
                               interceptorRegistry: InterceptorRegistry): ConnectionDemuxer
}

