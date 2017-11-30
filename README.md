#### RSOCKET-ANDROID

Backport of [rsocket-java](https://github.com/rsocket/rsocket-java) intended for pre java8 runtimes.   
Written in kotlin using rxjava2, `OkHttp` is used as websockets transport.
Target platform is Android (tested on API level 4.4+)  

Supports 4 interaction models: fire-and-forget, request-response, request-stream, request-channel.  
   
##### Build and binaries

For now project is not released yet, snapshots can be installed locally with `./gradlew install`  
This will produce 2 artifacts:   
 ```groovy
 dependencies {  
     compile 'io.rsocket:rsocket-android-core:0.9-SNAPSHOT'    
     compile 'io.rsocket:rsocket-transport-okhttp:0.9-SNAPSHOT'         
   }    
  ```
  
  ##### USAGE
  Sample mobile application for verifying interactions is available [here](https://github.com/mostroverkhov/rsocket-backport-demo)  
  Client
  ```
  val rSocket: Single<RSocket> = RSocketFactory
              .connect()
              .transport(OkhttpWebsocketClientTransport
                      .create(protocol, host, port))
              .start()
   ```
   
   Request-stream  
   ```
   rSocket.flatMapPublisher { 
      it.requestStream(PayloadImpl("req-stream ping")) 
    }.subscribe { responsePayload -> Log.d("request-stream", responsePayload.getDataUtf8)}
   ```