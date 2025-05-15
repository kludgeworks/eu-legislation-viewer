plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.graalvm.native)
    // Temporarily disable CycloneDX plugin due to compatibility issues
    // alias(libs.plugins.cyclonedx.bom)
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

    // Kotlin
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.datetime)

    // Spring
    implementation(libs.spring.modulith.starter.core)
    implementation(libs.spring.shell.starter.jna)

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
        freeCompilerArgs.addAll("-Xjsr305=strict")
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