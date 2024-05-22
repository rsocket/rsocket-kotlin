# chat

* api - shared chat API for both client and server
* client - client API implementation via requesting to RSocket with Protobuf serialization.
  Works on JVM(TCP/WS), Native(TCP/WS), Js(WS).
  Tasks for running clients:
    * JVM: `run`
  * Native: `runDebugExecutable[TARGET]` / `runReleaseExecutable[TARGET]`
    (where `[TARGET]` is one of `LinuxX64`, `MacosArm64` or `MacosX64`)
  * NodeJs: `jsNodeRun` / `jsNodeDevelopmentRun` / `jsNodeProductionRun`
  * Browser: `jsBrowserRun` / `jsBrowserDevelopmentRun` / `jsBrowserProductionRun`
* server - server API implementation with storage in concurrent map
  and exposing it through RSocket with Protobuf serialization.
  Can be started on JVM(TCP/WS), Native(TCP/WS), NodeJS(TCP).
  Tasks for running servers:
    * JVM: `run`
  * Native: `runDebugExecutable[TARGET]` / `runReleaseExecutable[TARGET]`
    (where `[TARGET]` is one of `LinuxX64`, `MacosArm64` or `MacosX64`)
    * NodeJs: `jsNodeRun` / `jsNodeDevelopmentRun` / `jsNodeProductionRun`
