plugins {
    id("java-library")
}

dependencies {
    api(project(":framework-core"))
    api("org.testng:testng:7.12.0")
    api("org.assertj:assertj-core:3.27.6")
    // pinned to framework-core's declared version — do not let transitive deps drag a second Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.1")
    implementation("net.javacrumbs.json-unit:json-unit-assertj:5.1.0")
    implementation("com.networknt:json-schema-validator:1.5.9")
    api("io.qameta.allure:allure-java-commons:2.33.0")
    // allure-assertj removed: AllureAspectJ is now vendored in framework-reporting with a naming fix.
    // aspectjweaver remains here so the root build's jvmArgs hook can locate it on testRuntimeClasspath.
    implementation("org.aspectj:aspectjweaver:1.9.22")
}