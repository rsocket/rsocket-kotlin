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

import io.ktor.utils.io.core.*
import io.rsocket.kotlin.*
import io.rsocket.kotlin.payload.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.*

val rSocketAcceptor: ConnectionAcceptor = ConnectionAcceptor {
    RSocketRequestHandler {
        requestResponse {
            delay(Random.nextLong(1000, 3000))
            throw RSocketError.Invalid(it.toString())
        }
        requestStream {
            flow {
                coroutineScope {
                    while (isActive) {
                        emit(it)
                    }
                }
            }
        }
    }
}

suspend fun RSocket.doSomething() {
    //            launch { rSocket.requestResponse(Payload(byteArrayOf(1, 1, 1), byteArrayOf(2, 2, 2))) }
//        launch { rSocket.fireAndForget(Payload(byteArrayOf(1, 1, 1), byteArrayOf(2, 2, 2))) }
//        launch { rSocket.metadataPush(byteArrayOf(1, 2, 3)) }
    var i = 0
    requestStream(Payload(byteArrayOf(1, 1, 1), byteArrayOf(2, 2, 2))).buffer(10000).collect {
        println(it.data.readBytes().contentToString())
        if (++i == 10000) error("")
    }
//        launch { rSocket.requestChannel(RequestingFlow { emit(Payload(byteArrayOf(3, 3, 3), byteArrayOf(4, 4, 4))) }).collect() }
}
