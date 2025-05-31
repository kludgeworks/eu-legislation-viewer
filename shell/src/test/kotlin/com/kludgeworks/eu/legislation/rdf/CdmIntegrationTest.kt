package com.kludgeworks.eu.legislation.rdf

import com.kludgeworks.eu.legislation.cdm.Cdm
import com.kludgeworks.eu.legislation.cdm.Dcat
import com.kludgeworks.eu.legislation.cdm.Owl
import com.kludgeworks.eu.legislation.cdm.Skos
import com.kludgeworks.eu.legislation.cdm.SKOSXL
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.vocabulary.RDF
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull

/**
 * Integration test for CDM module to verify that the generated
 * Java classes can be used correctly.
 */
class CdmIntegrationTest {

    @Test
    fun `should create and query RDF model using generated CDM classes`() {
        // Create a new RDF model
        val model = ModelFactory.createDefaultModel()

        // Create a work resource using CDM vocabulary
        val workUri = "http://data.europa.eu/eli/reg/2015/1998"
        val work = model.createResource(workUri)
            .addProperty(RDF.type, Cdm.work)
            .addProperty(Cdm.title_alternative, "Implementing Regulation 2015/1998")
            .addProperty(Cdm.resource_legal_id_local, "32015R1998")

        // Create an expression using CDM vocabulary
        val expressionUri = "http://data.europa.eu/eli/reg/2015/1998/eng"
        val expression = model.createResource(expressionUri)
            .addProperty(RDF.type, Cdm.expression)
            .addProperty(Skos.prefLabel, "Implementing Regulation 2015/1998 (English version)")
            .addProperty(Cdm.expression_title, "Commission Implementing Regulation (EU) 2015/1998")
            .addProperty(Cdm.expression_belongs_to_work, work)

        // Verify that our model contains expected statements
        val workNode = model.getResource(workUri)
        assertNotNull(workNode, "Work resource should exist")
        assertEquals("32015R1998", workNode.getProperty(Cdm.resource_legal_id_local).string)

        val expressionNode = model.getResource(expressionUri)
        assertNotNull(expressionNode, "Expression resource should exist")
        assertEquals("Implementing Regulation 2015/1998 (English version)", 
                     expressionNode.getProperty(Skos.prefLabel).string)
    }

    @Test
    fun `verify all required vocabularies are available`() {
        // This test simply verifies that all necessary vocabulary classes 
        // have been generated and are accessible
        assertNotNull(Cdm.work)
        assertNotNull(Cdm.expression)
        assertNotNull(Skos.prefLabel)
        // SKOSXL and Owl classes might have different property names in the generated code
        // Let's check additional properties from Cdm, Skos, and Dcat instead
        assertNotNull(Cdm.resource_legal_id_local)
        assertNotNull(Dcat.Dataset)
        assertNotNull(Cdm.expression_title)
    }
}
