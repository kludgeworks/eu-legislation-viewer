import java.nio.file.Paths
import java.nio.file.Files
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.*

apply(plugin = "java")

data class FormexVersion(val file: String, val version: String) {
    companion object {
        fun fromFile(file: String): FormexVersion {
            // Extract version from filename pattern "formex-XX.XX.X-YYYYMMDD.xd"
            val versionRegex = "formex-(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-\\d+)?".toRegex()
            val match = versionRegex.find(file)

            val version = if (match != null) {
                val major = match.groupValues[1]
                val minor = match.groupValues[2]
                val patch = match.groupValues[3].takeIf { it.isNotEmpty() } ?: "0"

                "v${major.padStart(2, '0')}_${minor.padStart(2, '0')}_$patch"
            } else {
                // Fallback if pattern doesn't match
                "unknown_version"
            }

            return FormexVersion(file, version)
        }
    }

    // Use the simple common binding file
    fun bindingFile(): String {
        return "binding.xjb"
    }

    // Package name based on version
    val packageName: String
        get() = "com.kludgeworks.formex.model.$version"

    // Output directory path
    fun outputDir(projectDir: String): String {
        return "$projectDir/build/generated/sources/formex/${packageName.replace(".", "/")}"
    }
}

// Define a configuration for the XJC dependencies
val xjcConfiguration by configurations.creating {
    isVisible = false
    isTransitive = true
    isCanBeConsumed = false
    isCanBeResolved = true
    description = "Dependencies for XJC"
}

// Add dependencies required for XJC
dependencies {
    xjcConfiguration("org.glassfish.jaxb:jaxb-xjc:4.0.3")
    xjcConfiguration("com.sun.xml.bind:jaxb-impl:4.0.3")
    xjcConfiguration("jakarta.xml.bind:jakarta.xml.bind-api:4.0.1")
}

// Dynamically find all Formex XSD files
fun findFormexXsdFiles(): List<FormexVersion> {
    val xsdDir = project.file("src/main/resources/xsd")
    if (!xsdDir.exists()) {
        return emptyList()
    }

    return xsdDir.listFiles { file ->
        file.name.startsWith("formex-") && (file.name.endsWith(".xd") || file.name.endsWith(".xsd"))
    }?.map {
        FormexVersion.fromFile(it.name)
    } ?: emptyList()
}

// Register source directories for the main sourceSet
val formexVersions = findFormexXsdFiles()
formexVersions.forEach { version ->
    val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
    val mainSourceSet = sourceSets.getByName("main")
    mainSourceSet.java.srcDir(version.outputDir(project.projectDir.path))
}

tasks.register("generateFormexSources") {
    description = "Generates Java classes for all Formex schema versions"
    group = "build"

    doLast {
        val formexVersions = findFormexXsdFiles()

        formexVersions.forEach { version ->
            val filename = version.file
            val packageName = version.packageName
            val outputDir = version.outputDir(project.projectDir.path)

            // Create the output directory if it doesn't exist
            project.mkdir(outputDir)

            logger.lifecycle("Generating Java classes for Formex ${version.version}")
            logger.lifecycle("Using XSD file: $filename")
            logger.lifecycle("Using binding file: ${version.bindingFile()}")
            logger.lifecycle("Package name: $packageName")

            // Execute the XJC command
            project.exec {
                workingDir = project.projectDir
                executable = "java"

                // Build the classpath string from the configuration
                val classpathString = project.configurations.getByName("xjcConfiguration").files.joinToString(separator = ":")

                // Build the command arguments
                val commandArgs = mutableListOf(
                    "-cp", classpathString,
                    "com.sun.tools.xjc.Driver",
                    "-d", outputDir,
                    "-p", packageName,
                    "-nv",             // No validation - critical for problematic schemas
                    "-extension",      // Enable JAXB extensions
                    "-XautoNameResolution", // Auto resolve name conflicts
                    "-Xinject-code",   // Allow code injection
                    "-disableXmlSecurity", // Disable XML security restrictions
                    "-no-header",      // Don't generate file headers
                    "-XexplicitAnnotation" // Generate annotations explicitly
                )

                // Add binding file
                val bindingFilePath = "${project.projectDir}/src/main/resources/xsd/${version.bindingFile()}"
                commandArgs.add("-b")
                commandArgs.add(bindingFilePath)

                // Add the XSD file
                commandArgs.add("${project.projectDir}/src/main/resources/xsd/$filename")

                args = commandArgs
            }
        }
    }
}

tasks.register<Delete>("cleanGeneratedSources") {
    description = "Cleans all generated JAXB sources"
    group = "build"

    formexVersions.forEach { version ->
        delete(version.outputDir(project.projectDir.path))
    }
}

// Make compileKotlin depend on the code generation
tasks.named("compileKotlin") {
    dependsOn("generateFormexSources")
}
