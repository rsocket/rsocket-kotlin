package io.rsocket.kotlin.util

import io.rsocket.kotlin.DuplexConnection

/**
 * [DuplexConnection] which delegates to given [source]
 */
open class DuplexConnectionProxy(protected val source: DuplexConnection)
    : DuplexConnection by source