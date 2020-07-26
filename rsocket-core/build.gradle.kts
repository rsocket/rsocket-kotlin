plugins {
    ids(Plugins.mppWithAtomic)
}

configureMultiplatform {
    dependenciesMain {
        api(Dependencies.ktor.io)
        compileOnly(Dependencies.atomicfuMetadata)
    }
    kampCommonTest.dependencies {
        api(KampModules.transportLocal)
    }
    //fix ktor issue
    js().sourceSetMain.dependencies {
        api(npm("text-encoding"))
    }
}
