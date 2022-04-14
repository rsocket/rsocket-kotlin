# chat

* api - shared chat API for both client and server
* client - client API implementation via requesting to RSocket with Protobuf serialization.
  Works on JVM(TCP/WS), Native(TCP/WS), NodeJS(WS/TCP), Browser(WS).
  Tasks for running clients:
    * JVM: `run`
    * Native: `runDebugExecutableNative` / `runReleaseExecutableNative`
    * NodeJs: `nodejsNodeRun` / `nodejsNodeDevelopmentRun` / `nodejsNodeProductionRun`
    * Browser: `browserBrowserRun` / `browserBrowserDevelopmentRun` / `browserBrowserProductionRun`
* server - server API implementation with storage in concurrent map
  and exposing it through RSocket with Protobuf serialization.
  Can be started on JVM(TCP/WS), Native(TCP/WS), NodeJS(TCP).
  Tasks for running servers:
    * JVM: `run`
    * Native: `runDebugExecutableNative` / `runReleaseExecutableNative`
    * NodeJs: `jsNodeRun` / `jsNodeDevelopmentRun` / `jsNodeProductionRun`
