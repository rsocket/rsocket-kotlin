# RSOCKET-KOTLIN
<a href='https://travis-ci.org/rsocket/rsocket-kotlin/builds'><img src='https://travis-ci.org/rsocket/rsocket-kotlin.svg?branch=master'></a> [![Join the chat at https://gitter.im/RSocket/reactivesocket-java](https://badges.gitter.im/RSocket/reactivesocket-java.svg)](https://gitter.im/ReactiveSocket/reactivesocket-java)

R(eactive)Socket: [Reactive Streams](http://www.reactive-streams.org/) over network boundary (tcp, websockets, etc) using Kotlin/Rxjava

RSocket is binary application protocol which models all communication as multiplexed streams of messages over a single network connection, and never synchronously blocks while waiting for a response.

It enables following symmetric interaction models:

*  fire-and-forget (no response)
* request/response (stream of 1)
* request/stream (finite/infinite stream of many)
*  channel (bi-directional streams)
*  per-stream and per-RSocket metadata 

## Build and Binaries

  Snapshots are available on Bintray
   ```groovy
    repositories {
        maven { url 'https://oss.jfrog.org/libs-snapshot' }
    }
```

```groovy
    dependencies {
        compile 'io.rsocket.kotlin:rsocket-core:0.9-SNAPSHOT'
    }
```
### Transports
`Netty` based Websockets and TCP transport (`Client` and `Server`)  
`OkHttp` based Websockets transport (`Client` only)
```groovy
 dependencies {
                compile 'io.rsocket.kotlin:rsocket-transport-netty:0.9-SNAPSHOT'
                compile 'io.rsocket.kotlin:rsocket-transport-okhttp:0.9-SNAPSHOT'
 }
```
### Usage
Each side of connection (Client and Server) has `Requester RSocket` for making requests to peer, and `Responder RSocket` to handle requests from peer.

Messages for all  interactions are represented as `Payload` of binary (`NIO ByteBuffer`) data   and metadata.

UTF-8 `text` payloads can be constructed as follows
```kotlin
val request = PayloadImpl.textPayload("data", "metadata")
```
Stream Metadata is optional
```kotlin
val request = PayloadImpl.textPayload("data")
```
#### Interactions
* Fire and Forget  
  `RSocket.fireAndForget(payload: Payload): Completable`  

* Request-Response  
   `RSocket.requestResponse(payload: Payload): Single<Payload>`  

* Request-Stream  
   `RSocket.requestStream(payload: Payload): Flowable<Payload>`  

* Request-Channel  
   `RSocket.requestChannel(payload: Publisher<Payload>): Flowable<Payload>`  

* Metadata-Push  
   `fun metadataPush(payload: Payload): Completable`  

#### Client
  Client is initiator of `Connections`
  ```kotlin
  val rSocket: Single<RSocket> = RSocketFactory               // Requester RSocket
              .connect()
              .acceptor { { requesterRSocket -> handler(requesterRSocket) } }  // Optional handler RSocket
              .transport(OkhttpWebsocketClientTransport       // WebSockets transport
                    .create(protocol, host, port))
              .start()

              private fun handler(requester:RSocket): RSocket {
                      return object : AbstractRSocket() {
                          override fun requestStream(payload: Payload): Flowable<Payload> {
                              return Flowable.just(PayloadImpl.textPayload("client handler response"))
                          }
                      }
                  }
```
#### Server
Server is acceptor of `Connections` from `Clients`
```kotlin
val closeable: Single<Closeable> = RSocketFactory
                .receive()
                .acceptor { { setup, rSocket -> handler(setup, rSocket) } } // server handler RSocket
                .transport(WebsocketServerTransport.create(port))  // Netty websocket transport
                .start()


private fun handler(setup: Setup, rSocket: RSocket): Single<RSocket> {
        return Single.just(object : AbstractRSocket() {
            override fun requestStream(payload: Payload): Flowable<Payload> {
                return Flowable.just(PayloadImpl.textPayload("server handler response"))
            }
        })
    }

```

### LICENSE

Copyright 2015-2018 Netflix, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.