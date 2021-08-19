plugins {
    id("rsocket-build")
    id("rsocket-build-publish")
}

kotlin {
    explicitApi()
    //TODO all warnings as errors
    sourceSets["commonTest"].dependencies {
        implementation(project(":rsocket-test"))
    }
}
