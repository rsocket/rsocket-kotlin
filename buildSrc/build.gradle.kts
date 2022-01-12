plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(buildLibs.build.kotlin)
    implementation(buildLibs.build.kotlinx.atomicfu)
}

kotlin {
    jvmToolchain {
        this as JavaToolchainSpec
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
