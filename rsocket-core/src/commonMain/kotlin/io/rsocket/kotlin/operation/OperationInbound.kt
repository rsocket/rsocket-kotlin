/*
 * Copyright 2015-2025 the original author or authors.
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

package io.rsocket.kotlin.operation

import io.rsocket.kotlin.*
import io.rsocket.kotlin.frame.*
import io.rsocket.kotlin.internal.*
import io.rsocket.kotlin.logging.*
import io.rsocket.kotlin.payload.*

internal interface OperationInbound {
    fun shouldReceiveFrame(frameType: FrameType): Boolean

    // payload is null when `next` flag was not set
    fun receivePayloadFrame(payload: Payload?, complete: Boolean): Unit = error("Payload frame is not expected to be received")
    fun receiveRequestNFrame(requestN: Int): Unit = error("RequestN frame is not expected to be received")
    fun receiveErrorFrame(cause: Throwable): Unit = error("Error frame is not expected to be received")
    fun receiveCancelFrame(): Unit = error("Cancel frame is not expected to be received")

    // for streaming case, when stream will not receive any more frames
    fun receiveDone() {}
}

@RSocketLoggingApi
internal class OperationFrameHandler(
    private val inbound: OperationInbound,
    private val frameLogger: Logger,
) {
    private val assembler = PayloadAssembler()

    fun close() {
        assembler.close()
    }

    fun handleDone() {
        inbound.receiveDone()
    }

    fun handleFrame(frame: Frame) {
        if (!inbound.shouldReceiveFrame(frame.type)) {
            frameLogger.debug { "Received unexpected frame: ${frame.dump(-1)}" }
            return frame.close()
        }

        when (frame) {
            is CancelFrame   -> inbound.receiveCancelFrame()
            is ErrorFrame    -> inbound.receiveErrorFrame(frame.throwable)
            is RequestNFrame -> inbound.receiveRequestNFrame(frame.requestN)
            is RequestFrame  -> {
                if (frame.initialRequest != 0) inbound.receiveRequestNFrame(frame.initialRequest)

                val payload = when {
                    // complete+follows=complete
                    frame.complete -> when {
                        frame.next -> assembler.assemblePayload(frame.payload)
                        // TODO[fragmentation] - what if we previously received fragment?
                        else       -> {
                            check(!assembler.hasPayload) { "wrong combination of frames" }
                            null
                        }
                    }

                    frame.next     -> when {
                        // if follows - then it's not the last fragment
                        frame.follows -> {
                            assembler.appendFragment(frame.payload)
                            return
                        }

                        else          -> assembler.assemblePayload(frame.payload)
                    }

                    else           -> error("wrong flags")
                }

                inbound.receivePayloadFrame(payload, frame.complete)

//                // TODO[fragmentation]: if there are no fragments saved and there are no following - we can ignore going through buffer
//                // TODO[fragmentation]: really, fragment could be NULL when `complete` is true, but `next` is false
//                if (frame.next || frame.type.isRequestType) appendFragment(frame.payload)
//                if (frame.complete) inbound.receivePayloadFrame(assemblePayload(), complete = true)
//                else if (!frame.follows) inbound.receivePayloadFrame(assemblePayload(), complete = false)
            }

            else             -> error("should not happen")
        }
    }
}
