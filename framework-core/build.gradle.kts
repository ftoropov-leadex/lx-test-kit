plugins {
    id("java-library")
}

dependencies {
    api("io.rest-assured:rest-assured:6.0.0")
    api("com.fasterxml.jackson.core:jackson-databind:2.21.1")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.21.1")
    implementation("org.slf4j:slf4j-api:2.0.17")
    runtimeOnly("org.apache.logging.log4j:log4j-core:2.25.3")
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.3")
    testImplementation("org.testng:testng:7.12.0")
    testImplementation("org.assertj:assertj-core:3.27.6")
}
