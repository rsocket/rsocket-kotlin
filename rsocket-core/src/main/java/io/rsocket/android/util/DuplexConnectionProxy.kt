package io.rsocket.android.util

import io.rsocket.android.DuplexConnection

open class DuplexConnectionProxy(protected val source: DuplexConnection)
    : DuplexConnection by source