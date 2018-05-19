package io.rsocket.kotlin.util

import io.rsocket.kotlin.DuplexConnection

open class DuplexConnectionProxy(protected val source: DuplexConnection)
    : DuplexConnection by source