plugins {
    ids(Plugins.benchmarks)
}

configureMultiplatform {
    dependenciesMain {
        implementation(Dependencies.kotlinx.benchmark)
    }
    kampCommonMain.dependencies {
        implementation(KampModules.core)
        implementation(KampModules.transportLocal)
    }
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

benchmark {
    targets {
        register("jvm")
    }
}
