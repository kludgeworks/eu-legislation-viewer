plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    implementation(project(":cellar-client"))
    
    // Spring Boot
    implementation(libs.spring.boot.starter)
    
    // Picocli
    implementation(libs.picocli.spring.boot.starter)
    
    // Kotlin
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.core)
    
    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.mockk)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Create a convenient task to run the CLI
tasks.register<JavaExec>("cli") {
    mainClass.set("com.kludgeworks.eu.legislation.shell.ShellApplicationKt")
    classpath = sourceSets["main"].runtimeClasspath
    args = System.getProperty("exec.args", "").split(" ").filter { it.isNotEmpty() }
}

// Configure Spring Boot JAR
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("eu-legislation-cli")
    archiveVersion.set("")
}
