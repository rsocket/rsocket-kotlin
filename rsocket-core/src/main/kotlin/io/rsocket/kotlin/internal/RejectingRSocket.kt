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
import io.rsocket.kotlin.DuplexConnection
import io.rsocket.kotlin.Frame
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.exceptions.RejectedSetupException

internal class RejectingRSocket(private val rSocket: Single<RSocket>) {

    fun with(connection: DuplexConnection): Single<RSocket> = rSocket
            .onErrorResumeNext { err ->
                connection
                        .sendOne(Frame.Error.from(0,
                                RejectedSetupException(err.message ?: "")))
                        .andThen(Single.error(err))
            }
}