####RSOCKET-ANDROID

Ongoing effort to make [rsocket-java](https://github.com/rsocket/rsocket-java) available pre java8. This project focus is client-server communication use case, and it complements original one - intended 
mainly for server-server. The goal is to make rsocket practically useful for native mobile (support android 4.4+) while interacting with jvm based backends (primary platform is `Spring Boot`). Backend is assumed to run original `rsocket-java`.  Interop with other tech stacks is not a goal of this project.
   
   
   Done so far (to make runnable POC)
   
   * Get rid of all modules except rsocket-core
   * Convert sources to kotlin (intellij semi auto converter helped a lot)
   * Replace jre8 specific types with kotlin or custom counterparts
   * Replace reactor types with rxjava2 ones
   * Added throw-away OkHttp based Websockets transport
   * Sample server running rsocket-java, and mobile app running rsocket-backport to verify supported interactions: 
     req-reply, req-stream, fire-and-forget. req-channel is not ported for now as there is no obvious way to convert it 
     to rxjava2 with minimal efforts
       
   TODO
   
   * Implement req-channel
   * Resurrect tests
   * Come up with Websockets transport with capabilities similar to Netty one, and minimal dependencies
   * introduce rpc system assuming jvm only environment - it should be approachable for regular developer to use/extend 
   * at least minimal integration with Spring Boot    