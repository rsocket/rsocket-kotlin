/*
 * Copyright 2015-2024 the original author or authors.
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

package io.rsocket.kotlin.transport

import kotlinx.coroutines.*
import kotlin.coroutines.*

@SubclassOptInRequired(RSocketTransportApi::class)
public interface RSocketTransport<Address, Target : RSocketTransportTarget> : CoroutineScope {
    public fun target(address: Address): Target
}

@SubclassOptInRequired(RSocketTransportApi::class)
public abstract class RSocketTransportFactory<
        Address,
        Target : RSocketTransportTarget,
        Transport : RSocketTransport<Address, Target>,
        Builder : RSocketTransportBuilder<Address, Target, Transport>,
        >(@PublishedApi internal val createBuilder: () -> Builder) {

    @OptIn(RSocketTransportApi::class)
    public inline operator fun invoke(
        context: CoroutineContext,
        block: Builder.() -> Unit = {},
    ): Transport = createBuilder().apply(block).buildTransport(context)
}

@SubclassOptInRequired(RSocketTransportApi::class)
public interface RSocketTransportBuilder<
        Address,
        Target : RSocketTransportTarget,
        Transport : RSocketTransport<Address, Target>,
        > {
    @RSocketTransportApi
    public fun buildTransport(context: CoroutineContext): Transport
}
