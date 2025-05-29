package com.kludgeworks.eu.legislation.cellarclient2.models.concepts

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import com.kludgeworks.eu.legislation.cellarclient2.models.core.URI

/**
 * Represents directory code concepts with a more flexible approach
 * to handle variable number of codes.
 */
@Serializable
data class ResourceLegalIsAboutConceptDirectoryCode(
    val type: String,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_IS_ABOUT_CONCEPT_DIRECTORY-CODE_1")
    val resourceLegalIsAboutConceptDirectoryCode1: DirectoryCodeConcept? = null,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_IS_ABOUT_CONCEPT_DIRECTORY-CODE_2")
    val resourceLegalIsAboutConceptDirectoryCode2: DirectoryCodeConcept? = null,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_IS_ABOUT_CONCEPT_DIRECTORY-CODE_3")
    val resourceLegalIsAboutConceptDirectoryCode3: DirectoryCodeConcept? = null
)

@Serializable
data class DirectoryCodeConcept(
    val type: String,
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI,
    @XmlElement
    @XmlSerialName("OP-CODE")
    val opCode: String,
    @XmlElement
    @XmlSerialName("IDENTIFIER")
    val identifier: String,
    @XmlElement
    @XmlSerialName("PREFLABEL")
    val prefLabel: String
)

// Legacy type aliases for backward compatibility
typealias ResourceLegalIsAboutConceptDirectoryCode1 = DirectoryCodeConcept
typealias ResourceLegalIsAboutConceptDirectoryCode2 = DirectoryCodeConcept
typealias ResourceLegalIsAboutConceptDirectoryCode3 = DirectoryCodeConcept
