/*
 * Copyright 2015-2018 the original author or authors.
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

package io.rsocket.kotlin

import io.reactivex.Completable

interface Closeable {
    /**
     * Close this `RSocket` upon subscribing to the returned `Publisher`
     *
     *
     * *This method is idempotent and hence can be called as many times at any point with same
     * outcome.*
     *
     * @return A `Publisher` that completes when this `RSocket` close is complete.
     */
    fun close(): Completable

    /**
     * Returns a `Publisher` that completes when this `RSocket` is closed. A `RSocket` can be closed by explicitly calling
     * [.close] or when the underlying transport
     * connection is closed.
     *
     * @return A `Publisher` that completes when this `RSocket` close is complete.
     */
    fun onClose(): Completable
}
