package io.rsocket

import io.rsocket.connection.*
import io.rsocket.error.*
import io.rsocket.frame.*
import io.rsocket.frame.io.*
import io.rsocket.keepalive.*
import io.rsocket.payload.*
import io.rsocket.server.*
import kotlinx.coroutines.*
import kotlin.test.*

class SetupRejectionTest {
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
