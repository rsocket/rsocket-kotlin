import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.mpp.*

enum class NativeTargets {
    Posix,
    Nix,
    Darwin
}

fun KotlinMultiplatformExtension.configureNative(
    nativeTargets: NativeTargets = NativeTargets.Posix,
    block: TargetBuilder.() -> Unit = {}
) {
    val nativeMain by sourceSets.creating {
        dependsOn(sourceSets["commonMain"])
    }
    val nativeTest by sourceSets.creating {
        dependsOn(sourceSets["commonTest"])
    }

    when (nativeTargets) {
        NativeTargets.Posix  -> posixTargets()
        NativeTargets.Nix    -> nixTargets()
        NativeTargets.Darwin -> darwinTargets()
    }.forEach {
        sourceSets["${it.name}Main"].dependsOn(nativeMain)
        sourceSets["${it.name}Test"].dependsOn(nativeTest)
    }

    targets.all {
        //add another test task with release binary
        if (this is KotlinNativeTargetWithTests<*>) {
            binaries.test(listOf(NativeBuildType.RELEASE))
            val releaseTest by testRuns.creating {
                setExecutionSourceFrom(binaries.getTest(NativeBuildType.RELEASE))
            }
        }
    }

    TargetBuilder(sourceSets, "native").block()
}

private fun KotlinMultiplatformExtension.darwinTargets(): List<KotlinNativeTarget> {
    val macosTargets = listOf(macosX64(), macosArm64())
    val iosTargets = listOf(iosArm32(), iosArm64(), iosX64(), iosSimulatorArm64())
    val tvosTargets = listOf(tvosArm64(), tvosX64(), tvosSimulatorArm64())
    val watchosTargets = listOf(watchosArm32(), watchosArm64(), watchosX86(), watchosX64(), watchosSimulatorArm64())
    return macosTargets + iosTargets + tvosTargets + watchosTargets
}

//ktor network supports only jvm and nix targets, but not mingw
private fun KotlinMultiplatformExtension.nixTargets(): List<KotlinNativeTarget> {
    return darwinTargets() + linuxX64()
}

//rsocket core and local transport supports all native targets, that are supported by ktor-io and kotlinx-coroutines
private fun KotlinMultiplatformExtension.posixTargets(): List<KotlinNativeTarget> {
    return nixTargets() + mingwX64()
}
