plugins {
    ids(Plugins.mpp)
}

configureMultiplatform {
    dependenciesMain {
        api(Dependencies.ktor.server)
        api(Dependencies.ktor.server.features.websockets)
    }
    kampCommonMain.dependencies {
        api(KampModules.transportWebsocket)
    }
}
