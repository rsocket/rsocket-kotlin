import dev.whyoleg.rsocket.*
import dev.whyoleg.rsocket.error.*
import dev.whyoleg.rsocket.flow.*
import dev.whyoleg.rsocket.payload.*
import kotlinx.coroutines.*
import kotlin.random.*

val rSocketAcceptor: RSocketAcceptor = {
    RSocketRequestHandler {
        requestResponse = {
            delay(Random.nextLong(1000, 3000))
            throw RSocketError.Invalid(it.toString())
        }
        requestStream = {
            RequestingFlow {
                coroutineScope {
                    while (isActive) {
                        emit(it)
                    }
                }
            }
        }
    }
}

suspend fun RSocket.doSomething() {
    //            launch { rSocket.requestResponse(Payload(byteArrayOf(1, 1, 1), byteArrayOf(2, 2, 2))) }
//        launch { rSocket.fireAndForget(Payload(byteArrayOf(1, 1, 1), byteArrayOf(2, 2, 2))) }
//        launch { rSocket.metadataPush(byteArrayOf(1, 2, 3)) }
    var i = 0
    requestStream(Payload(byteArrayOf(1, 1, 1), byteArrayOf(2, 2, 2))).collect(BufferStrategy(10000)) {
        println(it.data.contentToString())
        if (++i == 10000) error("")
    }
//        launch { rSocket.requestChannel(RequestingFlow { emit(Payload(byteArrayOf(3, 3, 3), byteArrayOf(4, 4, 4))) }).collect() }
}
