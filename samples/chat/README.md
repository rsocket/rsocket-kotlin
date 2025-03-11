# chat

* api - shared chat API for both client and server
* client - client API implementation via requesting to RSocket with Protobuf serialization.
  Works on JVM/Js/WasmJs/Native over TCP and WS.
  Tasks for running clients:
    * JVM: `jvmRun`
    * Native: `runDebugExecutable[TARGET]` / `runReleaseExecutable[TARGET]`
      (where `[TARGET]` is one of `LinuxX64`, `MacosArm64`, `MacosX64` or `MingwX64`)
    * JS: `jsNodeRun` / `jsNodeDevelopmentRun` / `jsNodeProductionRun`
    * WasmJs: `wasmJsNodeRun` / `wasmJsNodeDevelopmentRun` / `wasmJsNodeProductionRun`
* server - server API implementation with storage in concurrent map
  and exposing it through RSocket with Protobuf serialization.
  Can be started on JVM(TCP/WS), Native(TCP/WS), NodeJS(TCP).
  Tasks for running servers:
    * JVM: `jvmRun`
    * Native: `runDebugExecutable[TARGET]` / `runReleaseExecutable[TARGET]`
      (where `[TARGET]` is one of `LinuxX64`, `MacosArm64`, `MacosX64` or `MingwX64`)
    * JS: `jsNodeRun` / `jsNodeDevelopmentRun` / `jsNodeProductionRun`
    * WasmJs: `wasmJsNodeRun` / `wasmJsNodeDevelopmentRun` / `wasmJsNodeProductionRun`
