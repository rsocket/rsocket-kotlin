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
Transports are implemented based on [ktor](https://github.com/ktorio/ktor) to ensure Kotlin multiplatform. 
So it depends on `ktor` engines for available transports and platforms (JVM, JS, Native):
* JVM - TCP and WebSocket for both client and server
* JS - WebSocket client only
* [SOON] Native - TCP for both client and server

## Interactions

RSocket interface contains 5 methods:
* Fire and Forget: 

  `suspend fun fireAndForget(payload: Payload)`
* Request-Response:
  
  `suspend requestResponse(payload: Payload): Payload`
* Request-Stream: 
  
  `fun requestStream(payload: Payload): Flow<Payload>`
* Request-Channel: 

  `fun requestChannel(payloads: Flow<Payload>): Flow<Payload>`
* Metadata-Push:
  
  `suspend fun metadataPush(metadata: ByteReadPacket)`

## Using in your projects
The `master` branch is now dedicated to development of multiplatform rsocket-kotlin.
For now only snapshots are available via [oss.jfrog.org](oss.jfrog.org) (OJO).

Make sure, that you use Kotlin 1.4.

### Gradle:


```groovy
repositories {
    maven { url 'https://oss.jfrog.org/oss-snapshot-local' }
}
dependencies {
    implementation 'io.rsocket.kotlin:rsocket-core:0.10.0-SNAPSHOT'
    implementation 'io.rsocket.kotlin:rsocket-transport-ktor:0.10.0-SNAPSHOT'

//  client feature for ktor
//    implementation 'io.rsocket.kotlin:rsocket-transport-ktor-client:0.10.0-SNAPSHOT'

//  server feature for ktor 
//    implementation 'io.rsocket.kotlin:rsocket-transport-ktor-server:0.10.0-SNAPSHOT' 

//  one of ktor engines to work with websockets
//  client engines
//    implementation 'io.ktor:ktor-client-js:1.4.1' //js
//    implementation 'io.ktor:ktor-client-cio:1.4.1' //jvm
//    implementation 'io.ktor:ktor-client-okhttp:1.4.1' //jvm

//  server engines (jvm only)
//    implementation 'io.ktor:ktor-server-cio:1.4.1'
//    implementation 'io.ktor:ktor-server-netty:1.4.1'
//    implementation 'io.ktor:ktor-server-jetty:1.4.1'
//    implementation 'io.ktor:ktor-server-tomcat:1.4.1'
}
```

### Gradle Kotlin DSL:

```kotlin
repositories {
    maven("https://oss.jfrog.org/oss-snapshot-local")
}
dependencies {
    implementation("io.rsocket.kotlin:rsocket-core:0.10.0-SNAPSHOT")
    implementation("io.rsocket.kotlin:rsocket-transport-ktor:0.10.0-SNAPSHOT")

//  client feature for ktor
//    implementation("io.rsocket.kotlin:rsocket-transport-ktor-client:0.10.0-SNAPSHOT")

//  server feature for ktor 
//    implementation("io.rsocket.kotlin:rsocket-transport-ktor-server:0.10.0-SNAPSHOT") 

//  one of ktor engines to work with websockets
//  client engines
//    implementation("io.ktor:ktor-client-js:1.4.1") //js
//    implementation("io.ktor:ktor-client-cio:1.4.1") //jvm
//    implementation("io.ktor:ktor-client-okhttp:1.4.1") //jvm

//  server engines (jvm only)
//    implementation("io.ktor:ktor-server-cio:1.4.1")
//    implementation("io.ktor:ktor-server-netty:1.4.1")
//    implementation("io.ktor:ktor-server-jetty:1.4.1")
//    implementation("io.ktor:ktor-server-tomcat:1.4.1")
}
```

## Usage

### Client WebSocket with CIO ktor engine

```kotlin
//create ktor client
val client = HttpClient(CIO) {
    install(WebSockets)
    install(RSocketClientSupport) {
        //configure rSocket client (all values have defaults)
        
        keepAlive = KeepAlive(
            interval = 30.seconds,
            maxLifetime = 2.minutes 
        )
        
        //payload for setup frame
        setupPayload = Payload(...)
            
        //mime types
        payloadMimeType = PayloadMimeType(
            data = "application/json",
            metadata = "application/json"
        )   
        
        //optional acceptor for server requests
        acceptor = {
            RSocketRequestHandler {
                requestResponse = { it } //echo request payload
            }
        }
    }   
}

//connect to some url
val rSocket: RSocket = client.rScoket("wss://rsocket-demo.herokuapp.com/rsocket")

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
    install(RSocketServerSupport) {
        //configure rSocket server (all values have defaults)
        
        //install interceptors
        plugin = Plugin(
            connection = listOf(::SomeConnectionInterceptor)
        )   
    }   
    //configure routing
    routing {
        //configure route `url:port/rsocket`
        rSocket("rsocket") {
            RSocketRequestHandler {
                //handler for request/response
                requestResponse = { request: Payload ->
                    //... some work here
                    delay(500) // work emulation
                    Payload("data", "metadata")
                }         
                //handler for request/stream      
                requestStream = { request: Payload ->
                    flow {
                        repeat(1000) { i -> 
                            emit(Payload("data: $i"))
                        }               
                    }        
                }           
            }       
        }       
    }
}.start(true)
```

### More examples:

- [interactions](examples/interactions) - contains usages of some supported functions
- [multiplatform-chat](examples/multiplatform-chat) - chat implementation with JVM server and JS/JVM client with shared classes
and serializing data using [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)  
- [nodejs-tcp-transport](examples/nodejs-tcp-transport) - implementation of TCP transport for nodejs 

## Reactive Streams Semantics

From [RSocket protocol](https://github.com/rsocket/rsocket/blob/master/Protocol.md#reactive-streams-semantics):

    Reactive Streams semantics are used for flow control of Streams, Subscriptions, and Channels. 
    This is a credit-based model where the Requester grants the Responder credit for the number of PAYLOADs it can send. 
    It is sometimes referred to as "request-n" or "request(n)".

`kotlinx.coroutines` doesn't truly support `request(n)` semantic, but it has `Flow.buffer(n)` operator
which can be used to achieve something similar:

Example:

```kotlin
//assume we have client
val client: RSocket = TODO()

//and stream
val stream: Flow<Payload> = client.requestStream(Payload("data"))

//now we can use buffer to tell underlying transport to request values in chunks
val bufferedStream: Flow<Payload> = stream.buffer(10) //here buffer is 10, if `buffer` operator is not used buffer is by default 64

//now you can collect as any other `Flow`
//just after collection first request for 10 elements will be sent
//after 10 elements collected, 10 more elements will be requested, and so on
bufferedStream.collect { payload: Payload ->
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
