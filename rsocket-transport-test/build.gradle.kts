plugins {
    ids(Plugins.mppWithAtomic)
}

configureMultiplatform {
    dependenciesMain {
        api(Dependencies.kotlin.test)
        implementation(Dependencies.kotlin.annotations.common)
        implementation(Dependencies.kotlin.annotations.junit)
    }
    kampCommonMain.dependencies {
        api(KampModules.core)
    }
}
