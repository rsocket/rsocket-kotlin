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
