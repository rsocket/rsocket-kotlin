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