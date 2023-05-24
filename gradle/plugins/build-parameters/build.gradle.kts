/*
 * Copyright 2015-2023 the original author or authors.
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
    id("org.gradlex.build-parameters") version "1.4.3"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

group = "rsocket.build"

buildParameters {
    enableValidation.set(false)
    string("version")
    string("versionSuffix")
    string("useKotlin") {
        fromEnvironment("KOTLIN_VERSION_OVERRIDE")
    }

    string("githubUsername")
    string("githubPassword")

    group("skip") {
        bool("test") {
            description.set("Skip running tests")
            defaultValue.set(false)
        }
        bool("link") {
            description.set("Skip linking native binaries")
            defaultValue.set(false)
        }
    }
}
