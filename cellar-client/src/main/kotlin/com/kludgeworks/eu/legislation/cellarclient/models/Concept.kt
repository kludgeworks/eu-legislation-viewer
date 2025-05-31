package com.kludgeworks.eu.legislation.cellarclient.models

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

/**
 * Base concept with all common properties
 */
@Serializable
data class Concept(
    val type: String,
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI,
    @XmlElement
    @XmlSerialName("OP-CODE")
    val opCode: String? = null,
    @XmlElement
    @XmlSerialName("IDENTIFIER")
    val identifier: String,
    @XmlElement
    @XmlSerialName("PREFLABEL")
    val prefLabel: String,
    @XmlElement
    @XmlSerialName("ALTLABEL")
    val altLabel: List<String> = emptyList(),
    @XmlElement
    @XmlSerialName("SAMEAS")
    val sameAs: List<SameAs> = emptyList()
)

/**
 * Eurovoc concept hierarchy - simplified structure
 */
@Serializable
data class EurovocConcept(
    val type: String,
    @XmlElement
    @XmlSerialName("WORK_IS_ABOUT_CONCEPT_EUROVOC_CONCEPT")
    val concept: Concept,
    @XmlElement
    @XmlSerialName("WORK_IS_ABOUT_CONCEPT_EUROVOC_TT")
    val thematicThesaurus: Concept,
    @XmlElement
    @XmlSerialName("WORK_IS_ABOUT_CONCEPT_EUROVOC_DOM")
    val domain: Concept,
    @XmlElement
    @XmlSerialName("WORK_IS_ABOUT_CONCEPT_EUROVOC_MTH")
    val microThesaurus: Concept
)

/**
 * Directory code concepts - simplified to a list
 */
@Serializable
data class DirectoryCodeConcepts(
    val type: String,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_IS_ABOUT_CONCEPT_DIRECTORY-CODE_1")
    val directoryCode1: Concept? = null,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_IS_ABOUT_CONCEPT_DIRECTORY-CODE_2")
    val directoryCode2: Concept? = null,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_IS_ABOUT_CONCEPT_DIRECTORY-CODE_3")
    val directoryCode3: Concept? = null,
    @XmlElement
    @XmlSerialName("RESOURCE_LEGAL_IS_ABOUT_CONCEPT_DIRECTORY-CODE_4")
    val directoryCode4: Concept? = null,
    val codes: List<DirectoryCode> = emptyList()
)

/**
 * Individual directory code
 */
@Serializable
data class DirectoryCode(
    val position: Int,
    val concept: Concept
)
