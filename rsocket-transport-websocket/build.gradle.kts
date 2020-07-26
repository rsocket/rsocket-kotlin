plugins {
    ids(Plugins.mpp)
}

configureMultiplatform {
    dependenciesMain {
        api(Dependencies.ktor.http.cio)
    }
    kampCommonMain.dependencies {
        api(KampModules.core)
    }

    dependenciesTest {
        api(Dependencies.ktor.client.engines.cio)
        api(Dependencies.ktor.server.engines.cio)
    }
    kampCommonTest.dependencies {
        api(KampModules.transportTest)
        api(KampModules.transportWebsocketClient)
        api(KampModules.transportWebsocketServer)
    }
}
