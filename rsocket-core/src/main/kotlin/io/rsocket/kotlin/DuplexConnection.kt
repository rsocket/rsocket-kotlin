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
package io.rsocket.kotlin

import io.reactivex.Completable
import io.reactivex.Flowable
import java.nio.channels.ClosedChannelException
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber

/** Represents a connection with input/output that the protocol uses.  */
interface DuplexConnection : Availability, Closeable {

    /**
     * Sends the source of [Frame]s on this connection and returns the `Publisher`
     * representing the result of this send.
     *
     * <h2>Flow control</h2>
     *
     * The passed `Publisher` must
     *
     * @param frame Stream of `Frame`s to send on the connection.
     * @return `Publisher` that completes when all the frames are written on the connection
     * successfully and errors when it fails.
     */
    fun send(frame: Publisher<Frame>): Completable

    /**
     * Sends a single `Frame` on this connection and returns the `Publisher` representing
     * the result of this send.
     *
     * @param frame `Frame` to send.
     * @return `Publisher` that completes when the frame is written on the connection
     * successfully and errors when it fails.
     */
     fun sendOne(frame: Frame): Completable = send(Flowable.just(frame))

    /**
     * Returns a stream of all `Frame`s received on this connection.
     *
     * <h2>Completion</h2>
     *
     * Returned `Publisher` *MUST* never emit a completion event ([ ][Subscriber.onComplete].
     *
     * <h2>Error</h2>
     *
     * Returned `Publisher` can error with various transport errors. If the underlying physical
     * connection is closed by the peer, then the returned stream from here *MUST* emit an
     * [ClosedChannelException].
     *
     * <h2>Multiple Subscriptions</h2>
     *
     * Returned `Publisher` is not required to support multiple concurrent subscriptions.
     * RSocket will never have multiple subscriptions to this source. Implementations *MUST*
     * emit an [IllegalStateException] for subsequent concurrent subscriptions, if they do not
     * support multiple concurrent subscriptions.
     *
     * @return Stream of all `Frame`s received.
     */
    fun receive(): Flowable<Frame>
}
