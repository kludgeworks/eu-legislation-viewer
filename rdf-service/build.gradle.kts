plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Spring
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.modulith.starter.core)
    
    // Kotlin
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    
    // RDF4J
    implementation(libs.rdf4j.repository.sparql)
    implementation(libs.rdf4j.repository.http)
    implementation(libs.rdf4j.queryresultio.sparqljson)
    implementation(libs.rdf4j.queryresultio.sparqlxml)
    implementation(libs.apache.httpclient)
    
    // Arrow
    implementation(libs.arrow.core)
    implementation(libs.arrow.fx.coroutines)
    
    // CDM vocabulary
    implementation(project(":cdm"))
    
    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.jena.arq)
}

dependencyManagement {
    imports {
        mavenBom(libs.spring.modulith.bom.get().toString())
    }
}


tasks.withType<Test> {
    useJUnitPlatform()
}

// Disable bootJar since this is a library module
tasks.bootJar {
    enabled = false
}

// Enable plain jar
tasks.jar {
    enabled = true
}