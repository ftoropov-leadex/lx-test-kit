plugins {
    id("java-library")
}

dependencies {
    api(project(":framework-core"))
    api(project(":framework-test-support"))
    api(project(":framework-reporting"))
    api(project(":framework-splunk"))
}
