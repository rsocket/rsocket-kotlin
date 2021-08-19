plugins {
    `kotlin-multiplatform`
}

kotlin {
    sourceSets.all {
        languageSettings.apply {
            progressiveMode = true
            languageVersion = "1.5"
            apiVersion = "1.5"

            useExperimentalAnnotation("kotlin.RequiresOptIn")

            if (name.contains("test", ignoreCase = true)) testOptIn()
        }
    }
}

tasks.whenTaskAdded {
    if (name.endsWith("test", ignoreCase = true)) onlyIf { !rootProject.hasProperty("skipTests") }
}

//workaround for https://youtrack.jetbrains.com/issue/KT-44884
configurations.matching { !it.name.startsWith("kotlinCompilerPluginClasspath") }.all {
    resolutionStrategy.eachDependency {
        val version = requested.version
        if (requested.group == "org.jetbrains.kotlinx" &&
            requested.name.startsWith("kotlinx-coroutines") &&
            version != null && !version.contains("native-mt")
        ) {
            useVersion("$version-native-mt")
        }
    }
}
