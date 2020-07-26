plugins {
    ids(Plugins.mpp)
}

configureMultiplatform {
    dependenciesMain {
        api(Dependencies.ktor.client)
    }
    kampCommonMain.dependencies {
        api(KampModules.transportWebsocket)
    }
}
