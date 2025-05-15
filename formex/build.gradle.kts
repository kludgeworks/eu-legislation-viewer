plugins {
    kotlin("jvm")
    `java-library`
}

group = "com.kludgeworks"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

apply(from = "gradle/formex-generation.gradle.kts")

dependencies {
    // JAXB dependencies
    implementation(libs.jakarta.xml.bind.api)
    implementation(libs.jaxb.runtime)
    implementation(libs.jaxb.xjc)
    implementation(libs.jaxb.xjc.sun)
    implementation(libs.jaxb.impl)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}
