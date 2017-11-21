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
 */

package io.rsocket.android.transport

import io.reactivex.Completable
import io.reactivex.Single
import io.rsocket.Closeable
import io.rsocket.DuplexConnection

/** A server contract for writing transports of RSocket.  */
interface ServerTransport<T : Closeable> : Transport {

    /**
     * Starts this server.
     *
     * @param acceptor An acceptor to process a newly accepted `DuplexConnection`
     * @return A handle to retrieve information about a started server.
     */
    fun start(acceptor: ConnectionAcceptor): Single<T>

    /** A contract to accept a new `DuplexConnection`.  */
    interface ConnectionAcceptor : (DuplexConnection) -> Completable {

        /**
         * Accept a new `DuplexConnection` and returns `Publisher` signifying the end of
         * processing of the connection.
         *
         * @param duplexConnection New `DuplexConnection` to be processed.
         * @return A `Publisher` which terminates when the processing of the connection finishes.
         */
        override operator fun invoke(duplexConnection: DuplexConnection): Completable
    }
}
