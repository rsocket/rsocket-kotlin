import org.jetbrains.kotlin.gradle.plugin.mpp.*

kotlin {
    linuxX64()
    macosX64()
    //ktor-network doesn't support windows
    if (!project.name.startsWith("rsocket-transport-ktor")) mingwX64() //TODO

    iosArm32()
    iosArm64()
    iosX64()

    tvosArm64()
    tvosX64()

    watchosArm32()
    watchosArm64()
    watchosX86()


    val nativeMain by sourceSets.creating {
        dependsOn(sourceSets["commonMain"])
    }
    val nativeTest by sourceSets.creating {
        dependsOn(sourceSets["commonTest"])
    }
    targets.all {
        if (this is KotlinNativeTarget) {
            sourceSets["${name}Main"].dependsOn(nativeMain)
            sourceSets["${name}Test"].dependsOn(nativeTest)

            if (this is KotlinNativeTargetWithTests<*>) {

                //run tests on release + mimalloc to reduce tests execution time
                //compilation is slower in that mode, but work with buffers is much faster
                //TODO do another check?
                if (System.getenv("CI") == "true") {
                    binaries.test(listOf(RELEASE))
                    testRuns.all { setExecutionSourceFrom(binaries.getTest(RELEASE)) }
                    compilations.all {
                        kotlinOptions.freeCompilerArgs += "-Xallocator=mimalloc"
                    }
                }
            }
        }
    }
}
