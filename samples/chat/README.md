# chat

* api - shared chat API for both client and server
* client - client API implementation as requests to RSocket with Protobuf serialization. Works on JVM(TCP/WS), JS(WS),
  Native(TCP). Tasks for running sample clients:
  * JVM: `run`
  * Native: `runDebugExecutableNative` / `runReleaseExecutableNative`
  * NodeJs: `jsNodeRun`
  * Browser: `jsBrowserRun`
* server - server API implementation with storage in ordinary concurrent map and exposing it through RSocket with
  Protobuf serialization. Can be started on JVM(TCP+WS) and Native(TCP). Tasks for running sample servers:
  * JVM: `run`
  * Native: `runDebugExecutableNative` / `runReleaseExecutableNative`
