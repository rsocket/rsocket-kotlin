# rsocket-kotlin

RSocket Kotlin multi-platform implementation based on [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines).

RSocket is a binary protocol for use on byte stream transports such as TCP, WebSockets and Aeron.

It enables the following symmetric interaction models via async message passing over a single connection:

- request/response (stream of 1)
- request/stream (finite stream of many)
- fire-and-forget (no response)
- event subscription (infinite stream of many)

Learn more at http://rsocket.io

## Supported platforms and transports :

Transports are implemented based on [ktor](https://github.com/ktorio/ktor) to ensure Kotlin multiplatform. So it depends on `ktor` engines
for available transports and platforms (JVM, JS, Native):

* JVM - TCP and WebSocket for both client and server
* JS - WebSocket client only
* Native - TCP (linux x64, macos, ios, watchos, tvos) for both client and server

## Interactions

RSocket interface contains 5 methods:

* Fire and Forget:

  `suspend fun fireAndForget(payload: Payload)`
* Request-Response:

  `suspend requestResponse(payload: Payload): Payload`
* Request-Stream:

  `fun requestStream(payload: Payload): Flow<Payload>`
* Request-Channel:

  `fun requestChannel(initPayload: Payload, payloads: Flow<Payload>): Flow<Payload>`
* Metadata-Push:

  `suspend fun metadataPush(metadata: ByteReadPacket)`

## Using in your projects

Make sure, that you use Kotlin 1.6.10

### Gradle:

```kotlin
repositories {
    mavenCentral()
}
dependencies {
    implementation("io.rsocket.kotlin:rsocket-core:0.14.3")

    // TCP ktor transport
    implementation("io.rsocket.kotlin:rsocket-transport-ktor:0.14.3")

    // WS ktor transport client plugin
    implementation("io.rsocket.kotlin:rsocket-transport-ktor-client:0.14.3")

    // WS ktor transport server plugin
    implementation("io.rsocket.kotlin:rsocket-transport-ktor-server:0.14.3")
}
```

For WS ktor transport, available client or server engine should be added:

```kotlin
dependencies {
    // client engines for WS transport
    implementation("io.ktor:ktor-client-js:1.6.7") //js
    implementation("io.ktor:ktor-client-cio:1.6.7") //jvm
    implementation("io.ktor:ktor-client-okhttp:1.6.7") //jvm

    // server engines for WS transport (jvm only)
    implementation("io.ktor:ktor-server-cio:1.6.7")
    implementation("io.ktor:ktor-server-netty:1.6.7")
    implementation("io.ktor:ktor-server-jetty:1.6.7")
    implementation("io.ktor:ktor-server-tomcat:1.6.7")
}
```

## Usage

### Client WebSocket with CIO ktor engine

```kotlin
//create ktor client
val client = HttpClient(CIO) {
    install(WebSockets)
    install(RSocketSupport) {
        connector = RSocketConnector {
            //configure rSocket connector (all values have defaults)
            connectionConfig {
                keepAlive = KeepAlive(
                    interval = 30.seconds,
                    maxLifetime = 2.minutes
                )

                //payload for setup frame
                setupPayload { buildPayload { data("hello world") } }

                //mime types
                payloadMimeType = PayloadMimeType(
                    data = "application/json",
                    metadata = "application/json"
                )
            }

            //optional acceptor for server requests
            acceptor {
                RSocketRequestHandler {
                    requestResponse { it } //echo request payload
                }
            }
        }
    }
}

//connect to some url
val rSocket: RSocket = client.rSocket("wss://demo.rsocket.io/rsocket")

//request stream
val stream: Flow<Payload> = rSocket.requestStream(Payload.Empty)

//take 5 values and print response
stream.take(5).collect { payload: Payload ->
    println(payload.data.readText())
}
```

### Server WebSocket with CIO ktor engine

```kotlin
//create ktor server
embeddedServer(CIO) {
    install(RSocketSupport) {
        //configure rSocket server (all values have defaults)
        server = RSocketServer {
            //install interceptors
            interceptors {
                forConnection(::SomeConnectionInterceptor)
            }
        }
    }
    //configure routing
    routing {
        //configure route `url:port/rsocket`
        rSocket("rsocket") {
            RSocketRequestHandler {
                //handler for request/response
                requestResponse { request: Payload ->
                    //... some work here
                    delay(500) // work emulation
                    buildPayload {
                        data("data")
                        metadata("metadata")
                    }
                }
                //handler for request/stream      
                requestStream { request: Payload ->
                    flow {
                        repeat(1000) { i ->
                            emit(buildPayload { data("data: $i") })
                        }
                    }
                }
            }
        }
    }
}.start(true)
```

### More samples:

- [multiplatform-chat](samples/chat) - chat implementation with JVM server and JS/JVM client with shared classes and
  serializing data using [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)

## Reactive Streams Semantics

From [RSocket protocol](https://github.com/rsocket/rsocket/blob/master/Protocol.md#reactive-streams-semantics):

    Reactive Streams semantics are used for flow control of Streams, Subscriptions, and Channels. 
    This is a credit-based model where the Requester grants the Responder credit for the number of PAYLOADs it can send. 
    It is sometimes referred to as "request-n" or "request(n)".

`kotlinx.coroutines` doesn't truly support `request(n)` semantic, but it has flexible `CoroutineContext`
which can be used to achieve something similar. `rsocket-kotlin` contains `RequestStrategy` coroutine context element, which defines,
strategy for sending of `requestN` frames.

Example:

```kotlin
//assume we have client
val client: RSocket = TODO()

//and stream
val stream: Flow<Payload> = client.requestStream(Payload("data"))

//now we can use `flowOn` to add request strategy to context of flow
//here we use prefetch strategy which will send requestN for 10 elements, when, there is 5 elements left to collect
//so on call `collect`, requestStream frame with requestN will be sent, and then, after 5 elements will be collected
//new requestN with 5 will be sent, so collect will be smooth 
stream.flowOn(PrefetchStrategy(requestSize = 10, requestOn = 5)).collect { payload: Payload ->
    println(payload.data.readText())
}
```

## Bugs and Feedback

For bugs, questions and discussions please use the [Github Issues](https://github.com/rsocket/rsocket-kotlin/issues).

## LICENSE

Copyright 2015-2020 the original author or authors.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
