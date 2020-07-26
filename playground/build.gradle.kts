plugins {
    ids(Plugins.mpp)
}

configureMultiplatform {
    dependenciesMain {
        api(Dependencies.ktor.client.engines.cio) //jvm engine
        api(Dependencies.ktor.client.engines.js) //js engine

        api(Dependencies.ktor.server.engines.cio) //jvm engine
    }
    kampCommonMain.dependencies {
        api(KampModules.transportLocal)
        api(KampModules.transportTcp)
        api(KampModules.transportWebsocketClient)
        api(KampModules.transportWebsocketServer)
    }
    //fix ktor issue
    js().sourceSetMain.dependencies {
        api(npm("utf-8-validate"))
        api(npm("abort-controller"))
        api(npm("bufferutil"))
        api(npm("fs"))
    }
}
