/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *//*


package io.rsocket

import io.netty.buffer.Unpooled
import io.rsocket.test.util.TestDuplexConnection
import io.rsocket.test.util.TestSubscriber
import io.rsocket.util.PayloadImpl
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class RSocketServerTest {

    @Rule
    internal val rule = ServerSocketRule()

    @Test(timeout = 2000)
    @Ignore
    @Throws(Exception::class)
    fun testHandleKeepAlive() {
        rule.connection.addToReceivedBuffer(Frame.Keepalive.from(Unpooled.EMPTY_BUFFER, true))
        val sent = rule.connection.awaitSend()
        assertThat("Unexpected frame sent.", sent.type, `is`(FrameType.KEEPALIVE))
        */
/*Keep alive ack must not have respond flag else, it will result in infinite ping-pong of keep alive frames.*//*

        assertThat(
                "Unexpected keep-alive frame respond flag.",
                Frame.Keepalive.hasRespondFlag(sent),
                `is`(false))
    }

    @Test(timeout = 2000)
    @Ignore
    @Throws(Exception::class)
    fun testHandleResponseFrameNoError() {
        val streamId = 4
        rule.connection.clearSendReceiveBuffers()

        rule.sendRequest(streamId, FrameType.REQUEST_RESPONSE)

        val sendSubscribers = rule.connection.sendSubscribers
        assertThat("Request not sent.", sendSubscribers, hasSize<Any>(1))
        assertThat("Unexpected error.", rule.errors, `is`(empty<Any>()))
        val sendSub = sendSubscribers.iterator().next()
        assertThat(
                "Unexpected frame sent.",
                rule.connection.awaitSend().type,
                anyOf(`is`(FrameType.COMPLETE), `is`(FrameType.NEXT_COMPLETE)))
    }

    @Test(timeout = 2000)
    @Ignore
    @Throws(Exception::class)
    fun testHandlerEmitsError() {
        val streamId = 4
        rule.sendRequest(streamId, FrameType.REQUEST_STREAM)
        assertThat("Unexpected error.", rule.errors, `is`(empty<Any>()))
        assertThat(
                "Unexpected frame sent.", rule.connection.awaitSend().type, `is`(FrameType.ERROR))
    }

    @Test(timeout = 20000)
    fun testCancel() {
        val streamId = 4
        val cancelled = AtomicBoolean()
        rule.setAcceptingSocket(
                object : AbstractRSocket() {
                    override fun requestResponse(payload: Payload): Mono<Payload> {
                        return Mono.never<Payload>().doOnCancel { cancelled.set(true) }
                    }
                })
        rule.sendRequest(streamId, FrameType.REQUEST_RESPONSE)

        assertThat("Unexpected error.", rule.errors, `is`(empty<Any>()))
        assertThat("Unexpected frame sent.", rule.connection.sent, `is`(empty<Any>()))

        rule.connection.addToReceivedBuffer(Frame.Cancel.from(streamId))
        assertThat("Unexpected frame sent.", rule.connection.sent, `is`(empty<Any>()))
        assertThat("Subscription not cancelled.", cancelled.get(), `is`(true))
    }

   internal class ServerSocketRule : AbstractSocketRule<RSocketServer>() {

        private var acceptingSocket: RSocket? = null

        override fun init() {
            acceptingSocket = object : AbstractRSocket() {
                override fun requestResponse(payload: Payload): Mono<Payload> {
                    return Mono.just(payload)
                }
            }
            super.init()
        }

        fun setAcceptingSocket(acceptingSocket: RSocket) {
            this.acceptingSocket = acceptingSocket
            connection = TestDuplexConnection()
            connectSub = TestSubscriber.create()!!
            errors = ConcurrentLinkedQueue()
            super.init()
        }

        override fun newRSocket(): RSocketServer {
            return RSocketServer(connection, acceptingSocket!!) { throwable -> errors.add(throwable) }
        }

        internal fun sendRequest(streamId: Int, frameType: FrameType) {
            val request = Frame.Request.from(streamId, frameType, PayloadImpl.EMPTY, 1)
            connection.addToReceivedBuffer(request)
            connection.addToReceivedBuffer(Frame.RequestN.from(streamId, 2))
        }
    }
}
*/
