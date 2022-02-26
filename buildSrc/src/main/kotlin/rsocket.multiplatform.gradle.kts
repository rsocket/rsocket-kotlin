plugins {
    org.jetbrains.kotlin.multiplatform
    `kotlinx-atomicfu`
}

kotlin {
    sourceSets.all {
        languageSettings {
            progressiveMode = true

            optIn("kotlin.RequiresOptIn")

            if (name.contains("test", ignoreCase = true)) optInForTest()
        }
    }
}
