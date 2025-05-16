import kotlin.math.abs

plugins {
    kotlin("jvm")
    `java-library`
    alias(libs.plugins.intershop.jaxb)
    alias(libs.plugins.cyclonedx.bom)
}

group = "com.kludgeworks"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// Define a common parent directory for all generated sources
val generatedSourcesDir = layout.buildDirectory.dir("generated/sources/formex").get().asFile

// Class to handle Formex version information
class FormexVersion(val file: String, val version: String) {
    // Use the simple common binding file
    fun bindingFile(): String {
        return "binding.xjb"
    }

    // Package name based on version
    val packageName: String
        get() = "com.kludgeworks.formex.model.$version"

    // Output directory path for this version
    fun outputDir(baseDir: File): String {
        return "$baseDir/${packageName.replace(".", "/")}"
    }
    
    // Create a unique registration name to avoid conflicts
    fun uniqueRegistrationName(): String {
        // Extract date from filename if possible
        val dateRegex = "-(\\d{8})".toRegex()
        val dateMatch = dateRegex.find(file)
        val datePart = if (dateMatch != null) "_${dateMatch.groupValues[1]}" else "_${abs(file.hashCode())}"
        
        return "${version}$datePart"
    }
}

// Function to parse Formex version from filename
fun parseFormexVersion(file: String): FormexVersion {
    // Modified regex pattern to clearly separate major, minor, and patch versions
    // Group 1: major version
    // Group 2: minor version or "cj"
    // Group 3: patch version (optional)
    val versionRegex = """formex-(\d+)\.(\d+|cj)(?:\.(\d+))?(?:-\d+)?""".toRegex()

    val match = versionRegex.find(file) ?:
    throw IllegalArgumentException("Invalid Formex file format: $file. Cannot extract version information.")

    val major = match.groupValues[1]
    val minor = match.groupValues[2]

    val version = if (minor == "cj") {
        // Special 'cj' format
        "v${major.padStart(2, '0')}_cj_0"
    } else {
        // Standard format
        val patch = match.groupValues[3].takeIf { it.isNotEmpty() } ?: "0"
        "v${major.padStart(2, '0')}_${minor.padStart(2, '0')}_$patch"
    }

    return FormexVersion(file, version)
}

// Find all Formex XSD files
fun findFormexXsdFiles(): List<FormexVersion> {
    val xsdDir = project.file("src/main/resources/xsd")
    if (!xsdDir.exists()) {
        return emptyList()
    }

    return xsdDir.listFiles { file ->
        file.name.startsWith("formex-") && (file.name.endsWith(".xd") || file.name.endsWith(".xsd"))
    }?.map {
        parseFormexVersion(it.name)
    } ?: emptyList()
}

val formexVersions = findFormexXsdFiles()

jaxb {
    javaGen {
        formexVersions.forEach { version ->
            // Register a configuration for this Formex version with a unique name
            register(version.uniqueRegistrationName()) {
                // Set the schema file
                schema = project.file("src/main/resources/xsd/${version.file}")
                
                // Set the binding file
                binding = project.file("src/main/resources/xsd/${version.bindingFile()}")
                
                // Configure the package name
                packageName = version.packageName
                
                // Set output directory
                outputDir = project.file(version.outputDir(generatedSourcesDir))
                
                // Configure XJC options to match previous implementation
                args = listOf(
                    "-nv",              // No validation
                    "-extension",       // Enable JAXB extensions
                    "-XautoNameResolution", // Auto resolve name conflicts
                    "-Xinject-code",    // Allow code injection
                    "-disableXmlSecurity", // Disable XML security restrictions
                    "-no-header",       // Don't generate file headers
                    "-XexplicitAnnotation" // Generate annotations explicitly
                )
            }
        }
    }
}

sourceSets {
    main {
        java.srcDir(generatedSourcesDir)
    }
}

dependencies {
    implementation(libs.jakarta.xml.bind.api)
    implementation(libs.jaxb.runtime)
    implementation(libs.jaxb.impl)
}

// Make compileKotlin depend on the JAXB task
tasks.named("compileKotlin") {
    dependsOn("jaxb")
}