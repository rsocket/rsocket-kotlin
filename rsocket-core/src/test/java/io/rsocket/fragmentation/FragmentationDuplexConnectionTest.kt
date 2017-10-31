/*
 * Copyright 2016 Netflix, Inc.
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
 *//*


package io.rsocket.fragmentation

import io.rsocket.DuplexConnection
import io.rsocket.Frame
import io.rsocket.FrameType
import io.rsocket.util.PayloadImpl
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.*
import org.reactivestreams.Publisher
import reactor.core.publisher.EmitterProcessor
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.nio.ByteBuffer
import java.util.concurrent.ThreadLocalRandom

*/
/**  *//*

class FragmentationDuplexConnectionTest {
    @Test
    fun testSendOneWithFragmentation() {
        val mockConnection = mock(DuplexConnection::class.java)
        `when`(mockConnection.send(ArgumentMatchers.any()))
                .then { invocation ->
                    val frames = invocation.getArgument<Publisher<Frame>>(0)

                    StepVerifier.create(frames).expectNextCount(16).verifyComplete()

                    Mono.empty<Any>()
                }
        `when`(mockConnection.sendOne(ArgumentMatchers.any(Frame::class.java))).thenReturn(Mono.empty<Void>())

        val data = createRandomBytes(16)
        val metadata = createRandomBytes(16)

        val frame = Frame.Request.from(
                1, FrameType.REQUEST_RESPONSE, PayloadImpl(data, metadata), 1)

        val duplexConnection = FragmentationDuplexConnection(mockConnection, 2)

        StepVerifier.create(duplexConnection.sendOne(frame)).verifyComplete()
    }

    @Test
    fun testShouldNotFragment() {
        val mockConnection = mock(DuplexConnection::class.java)
        `when`(mockConnection.sendOne(ArgumentMatchers.any(Frame::class.java))).thenReturn(Mono.empty<Void>())

        val data = createRandomBytes(16)
        val metadata = createRandomBytes(16)

        val frame = Frame.Cancel.from(1)

        val duplexConnection = FragmentationDuplexConnection(mockConnection, 2)

        StepVerifier.create(duplexConnection.sendOne(frame)).verifyComplete()

        verify(mockConnection, times(1)).sendOne(frame)
    }

    @Test
    fun testShouldFragmentMultiple() {
        val mockConnection = mock(DuplexConnection::class.java)
        `when`(mockConnection.send(ArgumentMatchers.any()))
                .then { invocation ->
                    val frames = invocation.getArgument<Publisher<Frame>>(0)

                    StepVerifier.create(frames).expectNextCount(16).verifyComplete()

                    Mono.empty<Any>()
                }
        `when`(mockConnection.sendOne(ArgumentMatchers.any(Frame::class.java))).thenReturn(Mono.empty<Void>())

        val data = createRandomBytes(16)
        val metadata = createRandomBytes(16)

        val frame1 = Frame.Request.from(
                1, FrameType.REQUEST_RESPONSE, PayloadImpl(data, metadata), 1)
        val frame2 = Frame.Request.from(
                2, FrameType.REQUEST_RESPONSE, PayloadImpl(data, metadata), 1)
        val frame3 = Frame.Request.from(
                3, FrameType.REQUEST_RESPONSE, PayloadImpl(data, metadata), 1)

        val duplexConnection = FragmentationDuplexConnection(mockConnection, 2)

        StepVerifier.create(duplexConnection.send(Flux.just(frame1, frame2, frame3))).verifyComplete()

        verify(mockConnection, times(3)).send(ArgumentMatchers.any())
    }

    @Test
    fun testReassembleFragmentFrame() {
        val data = createRandomBytes(16)
        val metadata = createRandomBytes(16)
        val frame = Frame.Request.from(
                1024, FrameType.REQUEST_RESPONSE, PayloadImpl(data, metadata), 1)
        val frameFragmenter = FrameFragmenter(2)
        val fragmentedFrames = frameFragmenter.fragment(frame)
        val processor = EmitterProcessor.create<Frame>(128)
        val mockConnection = mock(DuplexConnection::class.java)
        `when`(mockConnection.receive()).then { answer -> processor }

        val duplexConnection = FragmentationDuplexConnection(mockConnection, 2)

        fragmentedFrames.subscribe(processor)

        duplexConnection
                .receive()
                .log()
                .doOnNext { c -> println("here - " + c.toString()) }
                .subscribe()
    }

    private fun createRandomBytes(size: Int): ByteBuffer {
        val bytes = ByteArray(size)
        ThreadLocalRandom.current().nextBytes(bytes)
        return ByteBuffer.wrap(bytes)
    }
}
*/
