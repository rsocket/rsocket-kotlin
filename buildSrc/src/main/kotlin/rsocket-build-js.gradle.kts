kotlin {
    js {
        useCommonJs()
        //configure running tests for JS
        nodejs {
            testTask {
                useMocha {
                    timeout = "600s"
                }
            }
        }
        browser {
            testTask {
                useKarma {
                    useConfigDirectory(rootDir.resolve("js").resolve("karma.config.d"))
                    useChromeHeadless()
                }
            }
        }
    }
}
