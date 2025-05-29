import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.artifacts.dsl.DependencyHandler

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = "com.kludgeworks.eu.legislation"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Kotlin
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.core)

    // XML Serialization
    implementation("io.github.pdvrieze.xmlutil:serialization-jvm:0.84.3")

    // Ktor Client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.xml)

    // Jackson for XML
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.dataformat.xml)
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter") // Basic Spring Boot starter
    // implementation(libs.spring.boot.starter.web) // Uncomment if exposing REST endpoints from this module

    // Logging
    // Spring Boot already includes logback by default through spring-boot-starter

    // Testing
    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation(libs.kotlin.test.junit5)
    // Using direct dependency for ktor-client-mock as it's not in the version catalog
    testImplementation("io.ktor:ktor-client-mock:${libs.versions.ktor.get()}")
    testImplementation(libs.mockito.kotlin) // Using mockito-kotlin from the version catalog
    // Add kotlinx-coroutines-test for runTest
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${libs.versions.kotlinx.coroutines.get()}")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
        // Potentially other BOMs like Ktor if available and managed
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "21"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// This module is intended as a library, so a bootJar might not be needed
// If it's a standalone service, keep bootJar enabled.
// For now, assuming it's a library consumed by another Spring Boot app.
tasks.bootJar {
    enabled = false // Set to true if this module should be a runnable Spring Boot app
}

tasks.jar {
    enabled = true // Ensure a standard JAR is built
}
