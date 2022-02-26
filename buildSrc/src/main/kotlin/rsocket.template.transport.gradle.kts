plugins {
    id("rsocket.template.library")
}

kotlin {
    sourceSets {
        commonTest {
            dependencies {
                implementation(project(":rsocket-transport-tests"))
            }
        }
    }
}
