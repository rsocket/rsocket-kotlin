plugins {
    ids(Plugins.mpp)
}

configureMultiplatform {
    dependenciesMain {
        api(Dependencies.ktor.network)
    }
    kampCommonMain.dependencies {
        api(KampModules.core)
    }
    kampCommonTest.dependencies {
        api(KampModules.transportTest)
    }
}
