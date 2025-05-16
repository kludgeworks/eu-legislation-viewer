plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.cyclonedx.bom)
}

allprojects {
    group = "com.kludgeworks"
    version = "0.0.1-SNAPSHOT"
    
    apply {
        plugin("org.cyclonedx.bom")
    }
}

tasks.register("build") {
    // This task does nothing, it's just a placeholder
    // The actual build happens in the subprojects
    dependsOn(":shell:build")
    dependsOn(":formex:build")
}

tasks.register("test") {
    // This task does nothing, it's just a placeholder
    // The actual tests happen in the subprojects
    dependsOn(":shell:test")
    dependsOn(":formex:test")
}
