plugins {
    id("java-library")
}

dependencies {
    api(project(":framework-core"))
    implementation(project(":framework-test-support"))
    implementation("io.qameta.allure:allure-testng:2.33.0")
    implementation("org.apache.logging.log4j:log4j-core:2.25.3")
    // aspectjrt provides the AspectJ annotations (@Aspect, @Pointcut, etc.) needed to compile
    // the vendored AllureAspectJ. compileOnly: the weaver (aspectjweaver) is the runtime agent,
    // declared in framework-test-support where it is picked up by the root build jvmArgs hook.
    compileOnly("org.aspectj:aspectjrt:1.9.22")
    testImplementation("org.testng:testng:7.12.0")
}