plugins {
    ids(Plugins.mpp)
}

configureMultiplatform {
    kampCommonMain.dependencies {
        api(KampModules.core)
    }
    kampCommonTest.dependencies {
        api(KampModules.transportTest)
    }
}
