package com.kludgeworks.eu.legislation.cellarclient2.models.concepts

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import com.kludgeworks.eu.legislation.cellarclient2.models.core.URI

@Serializable
data class WorkIsAboutConceptEurovoc(
    val type: String,
    @XmlElement
    @XmlSerialName("WORK_IS_ABOUT_CONCEPT_EUROVOC_CONCEPT")
    val concept: WorkIsAboutConceptEurovocConcept,
    @XmlElement
    @XmlSerialName("WORK_IS_ABOUT_CONCEPT_EUROVOC_TT")
    val tt: WorkIsAboutConceptEurovocTT,
    @XmlElement
    @XmlSerialName("WORK_IS_ABOUT_CONCEPT_EUROVOC_DOM")
    val dom: WorkIsAboutConceptEurovocDOM,
    @XmlElement
    @XmlSerialName("WORK_IS_ABOUT_CONCEPT_EUROVOC_MTH")
    val mth: WorkIsAboutConceptEurovocMTH
)

@Serializable
data class WorkIsAboutConceptEurovocConcept(
    val type: String,
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI,
    @XmlElement
    @XmlSerialName("IDENTIFIER")
    val identifier: String,
    @XmlElement
    @XmlSerialName("PREFLABEL")
    val prefLabel: String,
    @XmlElement
    @XmlSerialName("ALTLABEL")
    val altLabel: String? = null
)

@Serializable
data class WorkIsAboutConceptEurovocTT(
    val type: String,
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI,
    @XmlElement
    @XmlSerialName("IDENTIFIER")
    val identifier: String,
    @XmlElement
    @XmlSerialName("PREFLABEL")
    val prefLabel: String,
    @XmlElement
    @XmlSerialName("ALTLABEL")
    val altLabel: List<String> = emptyList()
)

@Serializable
data class WorkIsAboutConceptEurovocDOM(
    val type: String,
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI,
    @XmlElement
    @XmlSerialName("IDENTIFIER")
    val identifier: String,
    @XmlElement
    @XmlSerialName("PREFLABEL")
    val prefLabel: String
)

@Serializable
data class WorkIsAboutConceptEurovocMTH(
    val type: String,
    @XmlElement
    @XmlSerialName("URI")
    val uri: URI,
    @XmlElement
    @XmlSerialName("IDENTIFIER")
    val identifier: String,
    @XmlElement
    @XmlSerialName("PREFLABEL")
    val prefLabel: String
)
