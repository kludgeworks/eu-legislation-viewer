plugins {
    kotlin("jvm")
    `java-library`
    alias(libs.plugins.cyclonedx.bom)
}

group = "com.kludgeworks"
version = "0.0.1-SNAPSHOT"

// CDM version
val cdmVersion = "4.13.2"

// Define directories for generated source code
val generatedSourcesDir = layout.buildDirectory.dir("generated/sources/cdm").get().asFile
val resourcesDir = "src/main/resources"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Apache Jena
    implementation(libs.jena.core)
    implementation(libs.jena.arq)
    implementation(libs.jena.cmds)
}

// Define a task to verify CDM RDF directory exists
val verifyCdmDir by tasks.registering {
    description = "Verifies the Common Data Model directory is available"
    
    doLast {
        val cdmDir = file("$resourcesDir/cdm/$cdmVersion")
        
        if (!cdmDir.exists()) {
            throw GradleException("CDM directory not found at $resourcesDir/cdm/$cdmVersion. Please ensure the CDM RDF files are in this directory.")
        }
        
        logger.lifecycle("Found CDM directory at $resourcesDir/cdm/$cdmVersion")
    }
}

// Define a data class for RDF file information
data class RdfFile(
    val filePath: String,
    val className: String,
    val namespaceUri: String
)

// Create a project property to store discovered RDF files
val rdfFilesList = mutableListOf<RdfFile>()

// Task to discover all RDF files and create mapping
val discoverRdfFiles by tasks.registering {
    description = "Discovers all RDF files in the CDM directory"
    dependsOn(verifyCdmDir)
    
    doLast {
        // Clear any previous entries
        rdfFilesList.clear()
        
        val cdmDir = file("$resourcesDir/cdm/$cdmVersion")
        val rdfFilePattern = Regex("^(.+)\\.rdf$")
        
        // Files to exclude - these cause issues with Java class generation
        val excludedFiles = setOf("ont-policy.rdf")
        
        // Common namespace mappings
        val namespaceMap = mapOf(
            "cdm" to "http://publications.europa.eu/ontology/cdm",
            "cdmplus" to "http://publications.europa.eu/ontology/cdm",
            "skos" to "http://www.w3.org/2004/02/skos/core",
            "owl" to "http://www.w3.org/2002/07/owl",
            "rdfs" to "http://www.w3.org/2000/01/rdf-schema#",
            "dcat" to "http://www.w3.org/ns/dcat",
            "euvoc" to "http://publications.europa.eu/ontology/euvoc",
            "org" to "http://www.w3.org/ns/org",
            "vcard" to "http://www.w3.org/2006/vcard/ns",
            "marc21" to "http://publications.europa.eu/ontology/marc21",
            "wgs84_pos" to "http://www.w3.org/2003/01/geo/wgs84_pos#"
        )
        
        cdmDir.listFiles { file -> 
            file.isFile && file.name.endsWith(".rdf") && !excludedFiles.contains(file.name)
        }?.forEach { file ->
            val match = rdfFilePattern.find(file.name)
            if (match != null) {
                val baseName = match.groupValues[1]
                // Handle special case formatting and ensure valid Java class name
                val className = when {
                    // Very specific special cases
                    baseName == "wgs84_pos" -> "WGS84Pos"
                    baseName == "skos-xl" -> "SKOSXL"
                    baseName == "ont-policy" -> "OntPolicy"
                    baseName == "import" -> "ImportVocab" // Avoid conflict with Java keyword
                    
                    // Handle files with cdm_ prefix (common pattern)
                    baseName.startsWith("cdm_") -> {
                        "CDM" + baseName.removePrefix("cdm_")
                            .split("_")
                            .joinToString("") { part -> part.replaceFirstChar { it.uppercase() } }
                    }
                    
                    // Handle files with hyphens in their names
                    baseName.contains("-") -> {
                        baseName.split("-")
                            .joinToString("") { part -> part.replaceFirstChar { it.uppercase() } }
                    }
                    
                    // Handle files with underscores in their names
                    baseName.contains("_") -> {
                        baseName.split("_")
                            .joinToString("") { part -> part.replaceFirstChar { it.uppercase() } }
                    }
                    
                    // Default case - uppercase first letter
                    else -> baseName.replaceFirstChar { it.uppercase() }
                }
                
                // Find a namespace URI for this file, defaulting to CDM namespace if unknown
                val namespaceUri = namespaceMap[baseName.lowercase()] ?: 
                                  "http://publications.europa.eu/ontology/cdm"
                
                val rdfFile = RdfFile(
                    filePath = file.absolutePath,
                    className = className,
                    namespaceUri = namespaceUri
                )
                
                rdfFilesList.add(rdfFile)
                logger.lifecycle("Discovered RDF file: ${file.name} -> ${rdfFile.className}")
            }
        }
        
        if (rdfFilesList.isEmpty()) {
            throw GradleException("No RDF files found in $resourcesDir/cdm/$cdmVersion")
        }
        
        logger.lifecycle("Found ${rdfFilesList.size} RDF files to process")
    }
}

// Function to create a schema generation task
fun createSchemaGenTask(
    rdfFile: RdfFile
): TaskProvider<JavaExec> {
    val taskName = "generate${rdfFile.className}Class"
    
    return tasks.register<JavaExec>(taskName) {
        description = "Generates Java class for ${rdfFile.className} from RDF"
        
        mainClass.set("jena.schemagen")
        classpath = configurations.runtimeClasspath.get()
        jvmArgs = listOf("-Xmx8g")

        standardOutput = System.out
        errorOutput = System.err
        
        doFirst {
            mkdir(generatedSourcesDir)
            logger.lifecycle("Generating ${rdfFile.className} from ${rdfFile.filePath}")
        }
        
        // Configure arguments for schemagen
        args = listOf(
            "--ontology",                        // Generate ontology classes
            "-e", "UTF-8",                       // Character encoding
            "-i", rdfFile.filePath,              // Input RDF file
            "-n", rdfFile.className,             // Class name to generate (must be a valid Java class name)
            "-o", "${generatedSourcesDir}",      // Output Directory
            "-a", rdfFile.namespaceUri,          // Base URI
            "--package", "com.kludgeworks.eu.legislation.cdm"
        )
    }
}

// Task to create schema generation tasks for all RDF files
val createSchemaGenTasks by tasks.registering {
    description = "Creates schema generation tasks for all RDF files"
    dependsOn(discoverRdfFiles)
    
    doLast {
        rdfFilesList.forEach { rdfFile ->
            createSchemaGenTask(rdfFile)
            logger.lifecycle("Created schema generation task for ${rdfFile.className}")
        }
    }
}

// Combined task for all schema generation
val runSchemagen by tasks.registering {
    description = "Runs Apache Jena schemagen to generate all Java classes from RDF files"
    dependsOn(createSchemaGenTasks)
    
    doLast {
        val schemaGenTaskNames = rdfFilesList.map { "generate${it.className}Class" }
        
        // Execute all schema generation tasks
        schemaGenTaskNames.forEach { taskName ->
            try {
                tasks.named(taskName).get().actions.forEach { action ->
                    action.execute(tasks.named(taskName).get())
                }
            } catch (e: Exception) {
                logger.error("Failed to execute task $taskName: ${e.message}")
                throw e
            }
        }
        
        // Check for files with hyphens in class names after generation
        val generatedFiles = generatedSourcesDir.listFiles()
        generatedFiles?.forEach { file ->
            if (file.name.contains("-")) {
                val newName = file.name.replace("-", "")
                logger.lifecycle("Renaming file with hyphen: ${file.name} -> $newName")
                file.renameTo(File(generatedSourcesDir, newName))
            }
        }
    }
}

// Source sets configuration
sourceSets {
    main {
        java.srcDir(generatedSourcesDir)
    }
}

// Make the main compilation task depend on the code generation task
tasks.compileJava {
    dependsOn(runSchemagen)
}

// Add generated source directories as a source set for Kotlin compilation
kotlin.sourceSets.main {
    kotlin.srcDir(generatedSourcesDir)
}