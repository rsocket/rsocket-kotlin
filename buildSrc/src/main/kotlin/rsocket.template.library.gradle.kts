plugins {
    id("rsocket.multiplatform")
    id("rsocket.publication")
}

kotlin {
    explicitApi()

    sourceSets {
        commonTest {
            dependencies {
                implementation(project(":rsocket-test"))
            }
        }
    }
}
