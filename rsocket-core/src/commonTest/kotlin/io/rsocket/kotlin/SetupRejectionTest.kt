/*
 * Copyright 2015-2020 the original author or authors.
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

package io.rsocket.kotlin

import io.rsocket.kotlin.connection.*
import io.rsocket.kotlin.core.*
import io.rsocket.kotlin.error.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.frame.io.*
import io.rsocket.kotlin.keepalive.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*
import kotlin.test.*

class SetupRejectionTest : SuspendTest {
    @Test
    fun responderRejectSetup() = test {
        val errorMessage = "error"
        val sendingRSocket = CompletableDeferred<RSocket>()
        val acceptor: RSocketAcceptor = {
            sendingRSocket.complete(it)
            error(errorMessage)
        }
        val connection = TestConnection()

        val server = RSocketServer(ConnectionProvider(connection))

        connection.sendToReceiver(SetupFrame(Version.Current, false, KeepAlive(), null, PayloadMimeType(), Payload.Empty))

        assertFailsWith(RSocketError.Setup.Rejected::class, errorMessage) { server.start(acceptor) }

        val frame = connection.receiveFromSender()
        assertTrue(frame is ErrorFrame)
        assertTrue(frame.throwable is RSocketError.Setup.Rejected)
        assertEquals(errorMessage, frame.throwable.message)

        val sender = sendingRSocket.await()
        assertFalse(sender.isActive)
    }

//    @Test
//    fun requesterStreamsTerminatedOnZeroErrorFrame() = test {
//        val errorMessage = "error"
//        val connection = TestConnection()
//        val requester = RSocketRequester(connection, StreamId.client(), KeepAlive(), RequestStrategy.Default, {})
//        val deferred = GlobalScope.async { requester.requestResponse(Payload.Empty) }
//        delay(100)
//        connection.sendToReceiver(ErrorFrame(0, RSocketError.ConnectionError(errorMessage)))
//        assertFailsWith<RSocketError.ConnectionError>(errorMessage) { deferred.await() }
//    }
//
//    @Test
//    fun requesterNewStreamsTerminatedAfterZeroErrorFrame() = test {
//        val errorMessage = "error"
//        val connection = TestConnection()
//        val requester = RSocketRequester(connection, StreamId.client(), KeepAlive(), RequestStrategy.Default, {})
//        connection.sendToReceiver(ErrorFrame(0, RSocketError.ConnectionError(errorMessage)))
//        assertFailsWith<RSocketError.ConnectionError>(errorMessage) { requester.requestResponse(Payload.Empty) }
//    }
}
