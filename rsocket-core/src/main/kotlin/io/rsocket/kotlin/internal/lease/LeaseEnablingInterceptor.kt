/*
 * Copyright 2018 Maksym Ostroverkhov
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.rsocket.kotlin.internal.lease

import io.reactivex.Flowable
import io.rsocket.kotlin.DuplexConnection
import io.rsocket.kotlin.Frame
import io.rsocket.kotlin.FrameType
import io.rsocket.kotlin.interceptors.DuplexConnectionInterceptor
import io.rsocket.kotlin.util.DuplexConnectionProxy

internal class LeaseEnablingInterceptor(private val leaseContext: LeaseContext)
    : DuplexConnectionInterceptor {

    override fun invoke(type: DuplexConnectionInterceptor.Type,
                        connection: DuplexConnection)
            : DuplexConnection =
            if (type === DuplexConnectionInterceptor.Type.SETUP) {
                LeaseEnablingConnection(connection, leaseContext)
            } else {
                connection
            }

    private class LeaseEnablingConnection(
            setupConnection: DuplexConnection,
            private val leaseContext: LeaseContext)
        : DuplexConnectionProxy(setupConnection) {

        override fun receive(): Flowable<Frame> {
            return super.receive()
                    .doOnNext(
                            { f ->
                                val enabled = f.type == FrameType.SETUP
                                        && Frame.Setup.leaseEnabled(f)
                                leaseContext.leaseEnabled = enabled
                            })
        }
    }
}


