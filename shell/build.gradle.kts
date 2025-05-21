plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.graalvm.native)
    alias(libs.plugins.cyclonedx.bom)
}

group = "com.kludgeworks"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Project dependencies
    implementation(project(":formex"))
    implementation(project(":cdm"))

    // Kotlin
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.reactive)

    // Arrow
    implementation(libs.arrow.core)
    implementation(libs.arrow.fx.coroutines)
    implementation(libs.arrow.optics)

    // Ktor
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)

    // Spring
    implementation(libs.spring.modulith.starter.core)
    implementation(libs.spring.shell.starter.jna)
    implementation(libs.spring.boot.starter.web)

    // Jackson
    implementation(libs.jackson.dataformat.xml)
    implementation(libs.jackson.module.kotlin)

    // Apache Jena
    implementation(libs.jena.core)
    implementation(libs.jena.arq)
    implementation(libs.jena.rdfconnection)

    // Other
    implementation(libs.jspecify)
    implementation(libs.spring.boot.starter.thymeleaf)

    // Test
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.spring.shell.starter.test)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.mockito.kotlin)
}

dependencyManagement {
    imports {
        mavenBom(libs.spring.shell.bom.get().toString())
        mavenBom(libs.spring.modulith.bom.get().toString())
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xcontext-receivers")
    }
}

tasks.bootJar {
    manifest {
        attributes ("Enable-Native-Access" to "ALL-UNNAMED")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<Test> {
    useJUnitPlatform()
}
