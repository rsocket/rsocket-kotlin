# RSOCKET-ANDROID

This is an implementation of [RSocket](http://rsocket.io/) - binary application protocol bringing [Reactive-Streams](http://www.reactive-streams.org/) semantics   
for network communications. 
`Kotlin` & `RxJava2` backport of [RSocket-java](https://github.com/rsocket/rsocket-java) intended for pre java8 runtimes.   
Relies on `OkHttp` as WebSockets transport. Target platform is Android (tested on API level 4.4+)  

Supports 4 interaction models: fire-and-forget, request-response, request-stream, request-channel.  
   
## Build and Binaries

<a href='https://travis-ci.org/rsocket/rsocket-java/builds'><img src='https://travis-ci.org/rsocket/rsocket-java.svg?branch=1.0.x'></a>
   
      
   The project is not released yet, so snapshots have to be installed locally with `./gradlew install`  
   This will produce 2 artifacts:   

```groovy
    dependencies {  
        compile 'io.rsocket:rsocket-android-core:0.9-SNAPSHOT'    
        compile 'io.rsocket:rsocket-transport-okhttp:0.9-SNAPSHOT'         
    }    
```
   
  ## USAGE
  Sample mobile application for verifying interactions is available [here](https://github.com/mostroverkhov/rsocket-backport-demo)  
  
  ### Client
  Client initiates new `Connections`. Because protocol is duplex, each side of connection has  
  `Requester` RSocket for making requests to peer, and `Responder` RSocket to handle  
   requests from peer. `Responder` RSocket is optional for Client.   
  
  ```
  val rSocket: Single<RSocket> = RSocketFactory               // Requester RSocket  
              .connect()
              .acceptor { -> handler() }                      // Optional responder RSocket  
              .transport(OkhttpWebsocketClientTransport       // WebSockets transport
                      .create(protocol, host, port))
              .start()
              
              private fun handler(): RSocket {
                      return object : AbstractRSocket() {
                          override fun fireAndForget(payload: Payload): Completable {
                              return Completable.fromAction {Log.d("tag", "fire-n-forget from server")}
                          }
                      }
                  }
   ```
   Messages for all 4 interactions are represented as `Payload` of binary (nio `ByteBuffer`) data   
   and metadata (to/from UTF8-string utility methods are available)
        
   Request-stream  
   ```
   rSocket.flatMapPublisher { 
      it.requestStream(PayloadImpl("req-stream ping")) 
    }.subscribe { responsePayload -> Log.d("request-stream", responsePayload.getDataUtf8)}
   ```
   
   Request-channel  
   ```
      rSocket.flatMapPublisher { 
         it.requestChannel(Flowable.just(
                PayloadImpl("req-channel1"),
                PayloadImpl("req-channel2"))) 
       }.subscribe { responsePayload -> Log.d("request-stream", responsePayload.getDataUtf8)}
   ```
   ### Server
   Server accepts new `Connections` from peers. Same as `Client` it has `Requester` and `Responder` RSockets.  
   As this project does not provide server implementation, use [RSocket-java](https://github.com/rsocket/rsocket-java) with `Netty` based `WebSockets`  
   transport. Check its [examples](https://github.com/rsocket/rsocket-java/tree/1.0.x/rsocket-examples) folder or sample [app](https://github.com/mostroverkhov/rsocket-backport-demo/tree/master/rsocket-server-netty) minimalistic server 
   
