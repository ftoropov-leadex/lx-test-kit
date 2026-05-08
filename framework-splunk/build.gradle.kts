plugins {
    id("java-library")
}

dependencies {
    api(project(":framework-core"))
    api("org.assertj:assertj-core:3.27.6")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("org.awaitility:awaitility:4.3.0")
    testImplementation("org.testng:testng:7.12.0")
}