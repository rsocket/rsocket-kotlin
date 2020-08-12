/*
 * Copyright 2015-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.jetbrains.kotlin.gradle.plugin.mpp.*

/*
 * Copyright 2015-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    ids(Plugins.benchmarks)
}

repositories {
    maven("https://repo.spring.io/libs-snapshot")
}

configureMultiplatform {
    val jvm = jvm().kampSourceSetMain //common jvm source set
    val kotlinJvm = jvm("kotlin").kampSourceSetMain //kotlin benchmark
    val javaJvm = jvm("java").kampSourceSetMain //java benchmark

    kotlinJvm.sourceSet.dependsOn(jvm.sourceSet)
    javaJvm.sourceSet.dependsOn(jvm.sourceSet)

    dependenciesMain {
        implementation(Dependencies.kotlinx.benchmark)
        implementation(Dependencies.kotlinx.coroutines)
    }

    kotlinJvm.dependencies {
        implementation(KampModules.core)
        implementation(KampModules.transportLocal)
    }

    javaJvm.dependencies {
        implementation(Dependencies.kotlinx.coroutines.reactor)
        implementation(Dependencies.rsocketJava.core)
        implementation(Dependencies.rsocketJava.local)
    }
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

benchmark {
    targets {
        register("jvm")
        register("kotlin")
        register("java")
    }
}

tasks.register<JavaExec>("jmhProfilers") {
    group = "benchmark"
    description = "Lists the available JMH profilers"
    classpath = (kotlin.targets["jvm"].compilations["main"] as KotlinJvmCompilation).runtimeDependencyFiles
    main = "org.openjdk.jmh.Main"
    args("-lprof")
}

fun registerJmhGCTask(target: String): TaskProvider<*> = tasks.register<JavaExec>("${target}BenchmarkGC") {
    group = "benchmark"

    val buildFolder = buildDir.resolve("benchmarks").resolve(target)
    val compilation = (kotlin.targets[target].compilations["main"] as KotlinJvmCompilation)
    classpath(
        file(buildFolder.resolve("classes")),
        file(buildFolder.resolve("resources")),
        compilation.runtimeDependencyFiles,
        compilation.output.allOutputs
    )
    main = "org.openjdk.jmh.Main"

    dependsOn("${target}BenchmarkCompile")
    args("-prof", "gc")
    args("-jvmArgsPrepend", "-Xmx3072m")
    args("-jvmArgsPrepend", "-Xms3072m")
    args("-foe", "true") //fail-on-error
    args("-v", "NORMAL") //verbosity [SILENT, NORMAL, EXTRA]
    args("-rf", "json")
    args("-rff", project.file("build/reports/benchmarks/$target/result.json").also { it.parentFile.mkdirs() })
}

val t1 = registerJmhGCTask("java")
val t2 = registerJmhGCTask("kotlin")

tasks.register("benchmarkGC") {
    group = "benchmark"
    dependsOn(t1.get())
    dependsOn(t2.get())
}
