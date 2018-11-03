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

package io.rsocket.kotlin.internal

import io.reactivex.Single
import io.reactivex.processors.UnicastProcessor
import io.rsocket.kotlin.Frame
import io.rsocket.kotlin.FrameType
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.exceptions.RejectedSetupException
import io.rsocket.kotlin.test.util.LocalDuplexConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RSocketRejectionHandlerTest {
    private lateinit var sender: UnicastProcessor<Frame>
    private lateinit var receiver: UnicastProcessor<Frame>
    private lateinit var conn: LocalDuplexConnection

    @Before
    fun setUp() {
        sender = UnicastProcessor.create<Frame>()
        receiver = UnicastProcessor.create<Frame>()
        conn = LocalDuplexConnection("test", sender, receiver)
    }

    @Test(timeout = 2_000)
    fun rejectError() {
        val expectedMsg = "error"
        val rejectingRSocket = rejectingRSocket(IllegalArgumentException(expectedMsg))
        rejectingRSocket.subscribe({}, {})
        val frame = sender.firstOrError().blockingGet()
        assertTrue(frame.type == FrameType.ERROR)
        assertTrue(frame.streamId == 0)
        val err = Exceptions.from(frame)
        assertTrue(err is RejectedSetupException)
        assertEquals(expectedMsg, err.message)
    }

    @Test(timeout = 2_000)
    fun rejectErrorEmptyMessage() {
        val rejectingRSocket = rejectingRSocket(RuntimeException())
        rejectingRSocket.subscribe({}, {})
        val frame = sender.firstOrError().blockingGet()
        val err = Exceptions.from(frame)
        assertTrue(err is RejectedSetupException)
        assertEquals("", err.message)
    }

    private fun rejectingRSocket(err: Throwable): Single<RSocket> {
        val rSocket = Single.error<RSocket>(err)
        return RSocketRejectionHandler(conn).rejectOnError(rSocket)
    }
}