package com.kludgeworks.eu.legislation.shell.commands

import com.kludgeworks.eu.legislation.domain.Amendment
import com.kludgeworks.eu.legislation.domain.Consolidation
import com.kludgeworks.eu.legislation.domain.Expression
import com.kludgeworks.eu.legislation.domain.LegalDocument
import com.kludgeworks.eu.legislation.shell.service.DownloadService
import com.kludgeworks.eu.legislation.shell.service.EuLegislationService
import com.kludgeworks.eu.legislation.shell.util.JsonFormatter
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.*
import kotlin.test.assertTrue

@ExtendWith(SpringExtension::class)
@SpringBootTest
class EuLegislationCommandsTest {

    @Autowired
    private lateinit var commands: EuLegislationCommands

    @MockBean
    private lateinit var legislationService: EuLegislationService

    @MockBean
    private lateinit var downloadService: DownloadService

    @Autowired
    private lateinit var jsonFormatter: JsonFormatter

    private val testCelexNumber = "32015R1998"
    private val today = LocalDate(2025, 5, 11)

    @BeforeEach
    fun setup() {
        // Create a test document
        val document = LegalDocument(
            celexNumber = testCelexNumber,
            uri = "http://publications.europa.eu/resource/celex/$testCelexNumber",
            title = "Test Document",
            documentDate = today,
            expressions = listOf(
                Expression(
                    uri = "http://publications.europa.eu/resource/celex/$testCelexNumber.ENG",
                    language = "ENG",
                    manifestations = emptyList()
                )
            ),
            amendments = listOf(
                Amendment(
                    uri = "http://publications.europa.eu/resource/celex/32018R0001",
                    celexNumber = "32018R0001",
                    date = today,
                    consolidations = listOf(
                        Consolidation(
                            uri = "http://publications.europa.eu/resource/celex/02015R1998-20180101",
                            celexNumber = "02015R1998-20180101",
                            consolidationDate = today,
                            eli = "eli/reg/2015/1998/oj"
                        )
                    )
                )
            )
        )

        // Mock the service methods
        `when`(legislationService.getLegislation(testCelexNumber)).thenReturn(document)
        `when`(legislationService.getCompleteLegalDocument(
            celexNumber = eq(testCelexNumber),
            anyBoolean(),
            anyBoolean(),
            anyBoolean(),
            anyBoolean(),
            anyBoolean()
        )).thenReturn(document)
        
        `when`(legislationService.getAmendmentsWithConsolidations(testCelexNumber, true)).thenReturn(document.amendments)
    }

    @Test
    fun testGetLegislation() {
        val result = commands.getLegislation(testCelexNumber, false)
        
        verify(legislationService).getLegislation(testCelexNumber)
        assertTrue(result.contains(testCelexNumber))
        assertTrue(result.contains("Test Document"))
    }

    @Test
    fun testGetLegislationJson() {
        val result = commands.getLegislation(testCelexNumber, true)
        
        verify(legislationService).getLegislation(testCelexNumber)
        assertTrue(result.contains(testCelexNumber))
        assertTrue(result.contains("Test Document"))
    }

    @Test
    fun testGetCompleteLegalDocument() {
        val result = commands.getCompleteLegalDocument(
            celex = testCelexNumber,
            includeExpressions = true,
            includeManifestations = true,
            includeItems = true,
            includeAmendments = true,
            includeConsolidations = true,
            json = false
        )
        
        verify(legislationService).getCompleteLegalDocument(
            celexNumber = eq(testCelexNumber),
            eq(true),
            eq(true),
            eq(true), 
            eq(true),
            eq(true)
        )
        
        assertTrue(result.contains(testCelexNumber))
        assertTrue(result.contains("Title: Test Document"))
        assertTrue(result.contains("Language: ENG"))
        assertTrue(result.contains("Amendments: 1"))
    }

    @Test
    fun testGetAmendmentsWithConsolidations() {
        val result = commands.getAmendmentsWithConsolidations(
            celex = testCelexNumber,
            includeConsolidations = true,
            json = false
        )
        
        verify(legislationService).getAmendmentsWithConsolidations(testCelexNumber, true)
        
        assertTrue(result.contains(testCelexNumber))
        assertTrue(result.contains("32018R0001"))
        assertTrue(result.contains("Consolidations:"))
        assertTrue(result.contains("02015R1998-20180101"))
    }
}