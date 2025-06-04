import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    `java-library`
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

    // XML Processing
    implementation("net.sf.saxon:Saxon-HE:12.3")
    implementation("javax.xml.bind:jaxb-api:2.3.1")
    
    // kotlinx.serialization XML
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2")
    implementation("io.github.pdvrieze.xmlutil:serialization:0.86.3")
    implementation("io.github.pdvrieze.xmlutil:core:0.86.3")

    // Spring Boot
    implementation(libs.spring.boot.starter)

    // Testing
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.spring.boot.starter.test)
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.set(listOf("-Xjsr305=strict"))
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
