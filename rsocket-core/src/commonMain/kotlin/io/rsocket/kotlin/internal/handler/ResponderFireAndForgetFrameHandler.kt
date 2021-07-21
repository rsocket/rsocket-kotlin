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

package io.rsocket.kotlin.internal.handler

import io.rsocket.kotlin.internal.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*

internal class ResponderFireAndForgetFrameHandler(
    private val id: Int,
    private val streamsStorage: StreamsStorage,
    private val responder: RSocketResponder,
) : ResponderFrameHandler() {

    override fun start(payload: Payload): Job = responder.handleFireAndForget(payload, this)

    override fun handleCancel() {
        streamsStorage.remove(id)
        job?.cancel("Request cancelled")
    }

    override fun handleRequestN(n: Int) {
        //ignore
    }

    override fun cleanup(cause: Throwable?) {
        //ignore
    }

    override fun onSendComplete() {
        streamsStorage.remove(id)
    }

    override fun onSendFailed(cause: Throwable): Boolean = false
}
