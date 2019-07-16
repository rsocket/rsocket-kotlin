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

package io.rsocket.transport.okhttp

import io.reactivex.Flowable
import io.rsocket.kotlin.DuplexConnection
import io.rsocket.kotlin.Frame
import org.reactivestreams.Publisher

class OkHttpWebSocketConnection internal constructor(private val ws: OkWebsocket)
    : DuplexConnection {

    override fun availability(): Double = if (ws.isOpen()) 1.0 else 0.0

    override fun close() = ws.close()

    override fun onClose() = ws.onClose()

    override fun send(frame: Publisher<Frame>) = ws.send(frame)

    override fun receive(): Flowable<Frame> = ws.receive()
}