package io.rsocket

//import io.rsocket.connection.*
//import io.rsocket.flow.*
//import io.rsocket.payload.*
//import kotlinx.coroutines.*
//import kotlinx.coroutines.channels.*
//import kotlinx.coroutines.flow.*
//import kotlinx.benchmark.*
//
//@Fork(value = 1)
//@State(Scope.Benchmark)
//@Warmup(iterations = 5)
//@Measurement(iterations = 5, time = 5)
//@BenchmarkMode(Mode.Throughput)
//class RSocketPerf {
//    lateinit var client: RSocket
//    lateinit var server: Job
//
//    @Setup
//    fun setup() {
//        val clientChannel = Channel<ByteArray>(Channel.UNLIMITED)
//        val serverChannel = Channel<ByteArray>(Channel.UNLIMITED)
//        val serverConnection = LocalConnection("server", clientChannel, serverChannel)
//        val clientConnection = LocalConnection("client", serverChannel, clientChannel)
//        runBlocking {
//            launch {
//                client = RSocketClient(ConnectionProvider(clientConnection)).connect()
//            }
//            launch {
//                server = RSocketServer(ConnectionProvider(serverConnection)).start {
//                    object : AbstractRSocketWithJob() {
//                        override fun fireAndForget(payload: Payload): Unit = Unit
//
//                        override suspend fun requestResponse(payload: Payload): Payload = Payload.Empty
//
//                        override fun requestStream(payload: Payload): RequestingFlow<Payload> = payloadFlow
//
//                        override fun requestChannel(payloads: RequestingFlow<Payload>): RequestingFlow<Payload> = payloads.onRequest()
//                    }
//                }
//            }
//        }
//    }
//
//    @TearDown
//    fun tearDown() {
//        runBlocking {
//            client.runCatching { cancelAndJoin() }
//            server.runCatching { cancelAndJoin() }
//        }
//    }
//
//    @Benchmark
//    fun fireAndForget() {
//        client.fireAndForget(Payload.Empty)
//    }
//
//    @Benchmark
//    fun requestResponse(bh: Blackhole) = runBlocking {
//        bh.consume(client.requestResponse(Payload.Empty))
//    }
//
//    @Benchmark
//    fun requestStreamWithRequestByOneStrategy(bh: Blackhole) = runBlocking {
//        client.requestStream(Payload.Empty).requesting(RequestStrategy(1) { 1 }).collect { bh.consume(it) }
//    }
//
//    @Benchmark
//    fun requestStreamWithRequestAllStrategy(bh: Blackhole) = runBlocking {
//        client.requestStream(Payload.Empty).requesting(RequestStrategy(Int.MAX_VALUE)).collect { bh.consume(it) }
//    }
//
//    @Benchmark
//    fun requestChannelWithRequestByOneStrategy(bh: Blackhole) = runBlocking {
//        client.requestChannel(payloadFlow).requesting(RequestStrategy(1) { 1 }).collect { bh.consume(it) }
//    }
//
//    @Benchmark
//    fun requestChannelWithRequestAllStrategy(bh: Blackhole) = runBlocking {
//        client.requestChannel(payloadFlow).requesting(RequestStrategy(Int.MAX_VALUE)).collect { bh.consume(it) }
//    }
//
//    companion object {
//        val payloadFlow = RequestingFlow {
//            repeat(1000) { emit(Payload.Empty) }
//        }
//    }
//}
