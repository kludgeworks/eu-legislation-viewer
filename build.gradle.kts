plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.4.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.graalvm.buildtools.native") version "0.10.6"
    id("org.cyclonedx.bom") version "1.10.0"
}

group = "com.kludgeworks"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")

    // Spring
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.shell:spring-shell-starter-jna")

    // Jackson
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Apache Jena
    implementation("org.apache.jena:jena-core:5.3.0")
    implementation("org.apache.jena:jena-arq:5.3.0")
    implementation("org.apache.jena:jena-rdfconnection:5.3.0")

    // Other
    implementation("org.jspecify:jspecify:1.0.0")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.springframework.shell:spring-shell-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.shell:spring-shell-dependencies:3.4.0")
        mavenBom("org.springframework.modulith:spring-modulith-bom:1.3.3")
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
}

tasks.withType<Test> {
    useJUnitPlatform()
}
