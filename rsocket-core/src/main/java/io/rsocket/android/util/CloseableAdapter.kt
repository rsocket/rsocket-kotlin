package io.rsocket.android.util

import io.reactivex.Completable
import io.reactivex.processors.AsyncProcessor
import io.rsocket.android.Closeable

class CloseableAdapter(private val closeFunction: () -> Unit) : Closeable {
    private val onClose = AsyncProcessor.create<Void>()

    override fun close(): Completable {
        return Completable.defer {
            closeFunction()
            onClose.onComplete()
            onComplete()
        }
    }

    override fun onClose(): Completable = onComplete()

    private fun onComplete(): Completable = onClose.ignoreElements()
}
