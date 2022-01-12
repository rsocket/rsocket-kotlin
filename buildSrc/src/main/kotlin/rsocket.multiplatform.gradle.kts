plugins {
    org.jetbrains.kotlin.multiplatform
    `kotlinx-atomicfu`
}

kotlin {
    sourceSets.all {
        languageSettings {
            progressiveMode = true

            optIn("kotlin.RequiresOptIn")

            //TODO: kludge, this is needed now,
            // as ktor isn't fully supports kotlin 1.5.3x opt-in changes
            // will be not needed after ktor 2.0.0
            optIn("io.ktor.utils.io.core.ExperimentalIoApi")

            if (name.contains("test", ignoreCase = true)) optInForTest()
        }
    }
}
