# EU Legislation Common Data Model (CDM) Module

This module handles the generation of Java classes from the EU Legislation Common Data Model (CDM) using Apache Jena's schemagen tool.

## Version Information

This module is set up to work with CDM version 4.13.2, which corresponds to the current version of the Common Data Model used by the Publications Office of the European Union.

## Features

- Automatic discovery and processing of all RDF files in the CDM directory
- Dynamic Java class generation from all available ontologies
- Intelligent namespace mapping for proper URI resolution
- Memory-optimized processing with configurable JVM settings

## How it works

1. The build process scans the RDF files located in `src/main/resources/cdm/4.13.2/`.
2. It automatically creates schema generation tasks for each RDF file found.
3. Apache Jena's schemagen tool generates Java classes from the RDF schemas.
4. The generated classes provide a type-safe way to interact with CDM concepts in Java code.

## Generated Classes

The process generates Java classes in the package `com.kludgeworks.eu.legislation.cdm` for each RDF file found:

- **CDM.java**: Core Common Data Model vocabulary
- **CDMPlus.java**: Extended Common Data Model vocabulary with additional properties 
- **SKOS.java**: Simple Knowledge Organization System vocabulary
- **SKOSXL.java**: SKOS Extension for Labels
- **DCAT.java**: Data Catalog Vocabulary
- **EUVOC.java**: EU Vocabularies ontology
- And many more, based on the RDF files available

These classes represent:
- Classes/concepts from the ontologies
- Properties and relationships between concepts
- Constants for common values

## RDF Files

The module processes all `.rdf` files found in the resources directory. For example:

| File | Description | Generated Class |
|------|-------------|----------------|
| cdm.rdf | Core Common Data Model | CDM |
| cdmplus.rdf | Extended Common Data Model | CDMPlus |
| skos.rdf | Simple Knowledge Organization System | SKOS |
| owl.rdf | Web Ontology Language | OWL |
| dcat.rdf | Data Catalog Vocabulary | DCAT |
| ... | (All other RDF files are processed) | ... |

## Using the Generated Classes

The generated Java classes can be used in your code by adding a dependency on this module:

```kotlin
implementation(project(":cdm"))
```

Then, you can import and use the generated classes:

```kotlin
import com.kludgeworks.eu.legislation.cdm.CDM
import com.kludgeworks.eu.legislation.cdm.SKOS
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.vocabulary.RDF

val model = ModelFactory.createDefaultModel()

// Create a work resource
val work = model.createResource("http://data.europa.eu/eli/reg/2015/1998")
    .addProperty(RDF.type, CDM.work)
    .addProperty(CDM.title_alternative, "Implementing Regulation 2015/1998")

// Create an expression
val expression = model.createResource("http://data.europa.eu/eli/reg/2015/1998/eng")
    .addProperty(RDF.type, CDM.expression)
    .addProperty(SKOS.prefLabel, "Implementing Regulation 2015/1998 (English version)")
```

## Extending the Module

To add support for new RDF files:

1. Simply add new RDF files to the `src/main/resources/cdm/4.13.2/` directory
2. The build system will automatically detect and generate classes for them
3. If your file uses a different namespace, add it to the `namespaceMap` in the build script

## Resources

For more information about the EU Common Data Model:
- [EU Vocabularies - Common Data Model](https://op.europa.eu/en/web/eu-vocabularies/cdm)
- [Common Data Model Documentation](http://publications.europa.eu/resource/cellar/eacea2d9-fd33-11e8-a96d-01aa75ed71a1.0001.01/DOC_1)
- [EU Publications Office Linked Data](https://op.europa.eu/en/web/cellar/cellar-data/metadata/knowledge-graph)