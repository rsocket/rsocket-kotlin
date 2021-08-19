kotlin {
    jvm {
        testRuns.all {
            executionTask.configure {
                // ActiveProcessorCount is used here, to make sure local setup is similar as on CI
                // Github Actions linux runners have 2 cores
                jvmArgs("-Xmx4g", "-XX:+UseParallelGC", "-XX:ActiveProcessorCount=2")
            }
        }
    }
}
