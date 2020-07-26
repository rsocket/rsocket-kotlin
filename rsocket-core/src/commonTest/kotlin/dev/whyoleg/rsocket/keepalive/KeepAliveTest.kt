package dev.whyoleg.rsocket.keepalive

//class KeepAliveTest {
//    private val connection = TestConnection()
//    private fun requester(keepAlive: KeepAlive = KeepAlive(100.milliseconds, 1.seconds)): RSocket =
//        RSocketRequester(connection, StreamId.client(), keepAlive, RequestStrategy.Default, {})
//
//
//    @OptIn(ExperimentalCoroutinesApi::class)
//    @Test
//    fun requesterSendKeepAlive() = test {
//        requester()
//        val list = connection.sentAsFlow().take(3).toList()
//        assertEquals(3, list.size)
//        list.forEach {
//            assertTrue(it is KeepAliveFrame)
//            assertTrue(it.respond)
//        }
//    }
//
//    @Test
//    fun rSocketNotCanceledOnPresentKeepAliveTicks() = test {
//        val rSocket = requester()
//        launch(connection) {
//            while (isActive) {
//                delay(100.milliseconds)
//                connection.sendToReceiver(KeepAliveFrame(true, 0, byteArrayOf()))
//            }
//        }
//        delay(2.seconds)
//        assertTrue(rSocket.isActive)
//    }
//
//    @OptIn(ExperimentalCoroutinesApi::class)
//    @Test
//    fun requesterRespondsToKeepAlive() = test {
//        requester(KeepAlive(100.seconds, 100.seconds))
//        launch(connection) {
//            while (isActive) {
//                delay(100.milliseconds)
//                connection.sendToReceiver(KeepAliveFrame(true, 0, byteArrayOf()))
//            }
//        }
//
//        val list = connection.sentAsFlow().take(3).toList()
//        assertEquals(3, list.size)
//        list.forEach {
//            assertTrue(it is KeepAliveFrame)
//            assertFalse(it.respond)
//        }
//    }
//
//    @Test
//    fun noKeepAliveSentAfterRSocketCanceled() = test {
//        requester().cancel()
//        delay(500.milliseconds)
//        assertEquals(0, connection.sentFrames.size)
//    }
//
//    @OptIn(InternalCoroutinesApi::class)
//    @Test
//    fun rSocketCanceledOnMissingKeepAliveTicks() = test {
//        val rSocket = requester()
//        delay(2.seconds)
//        assertFalse(rSocket.isActive)
//        assertTrue(rSocket.getCancellationException().cause is RSocketError.ConnectionError)
//    }
//
//}
