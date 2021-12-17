plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(buildLibs.build.kotlin)
    implementation(buildLibs.build.kotlinx.atomicfu)
}
