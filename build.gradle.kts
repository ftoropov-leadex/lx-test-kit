import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    `java-library`
}

allprojects {
    repositories {
        mavenCentral()
    }
}

/* =========================
   Java + publish config
   ========================= */

subprojects {

    group = "systems.leadex.lxtestkit"
    version="1.0.4"
    plugins.withId("java-library") {

        apply(plugin = "maven-publish")

        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("gpr") {
                    from(components["java"])
                }
            }
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/ftoropov-leadex/lx-test-kit")
                    credentials {
                        username = System.getenv("GITHUB_ACTOR")
                        password = System.getenv("GITHUB_TOKEN")
                    }
                }
            }
        }

        extensions.configure<org.gradle.api.plugins.JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
            withJavadocJar()
            withSourcesJar()
        }

        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.release.set(21)
        }

        tasks.withType<Javadoc>().configureEach {
            (options as StandardJavadocDocletOptions)
                .addStringOption("Xdoclint:none", "-quiet")
        }

        tasks.withType<Test>().configureEach {
            useTestNG()

            doFirst {
                val weaver = configurations.findByName("testRuntimeClasspath")
                    ?.files
                    ?.find { it.name.contains("aspectjweaver") }

                if (weaver != null) {
                    jvmArgs("-javaagent:${weaver.absolutePath}")
                }
            }

            testLogging {
                events = setOf(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
                exceptionFormat = TestExceptionFormat.FULL
                showExceptions = true
                showStackTraces = true
            }

            val allureResultsDir = project.layout.buildDirectory.dir("allure-results")
            systemProperty("allure.results.directory", allureResultsDir.get().asFile.absolutePath)

            doFirst {
                delete(allureResultsDir)
            }
        }
    }
}
