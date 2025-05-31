package com.kludgeworks.eu.legislation.shell

import com.kludgeworks.eu.legislation.cellarclient.CellarApiClient
import com.kludgeworks.eu.legislation.cellarclient.models.*
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import picocli.CommandLine

class WorkCommandTest {
    
    private lateinit var cellarApiClient: CellarApiClient
    private lateinit var workCommand: WorkCommand
    private lateinit var commandLine: CommandLine
    
    @BeforeEach
    fun setUp() {
        cellarApiClient = mockk()
        workCommand = WorkCommand(cellarApiClient)
        commandLine = CommandLine(workCommand)
    }
    
    @Test
    fun `should display work information when resource exists`() {
        // Given
        val resourceId = "32015R1998"
        val mockNotice = createMockNotice()
        
        coEvery { cellarApiClient.fetchNotice(resourceId) } returns mockNotice
        
        // When
        val exitCode = commandLine.execute("32015R1998")
        
        // Then
        assertEquals(0, exitCode)
    }
    
    @Test
    fun `should return error code when resource not found`() {
        // Given
        val resourceId = "INVALID"
        
        coEvery { cellarApiClient.fetchNotice(resourceId) } returns null
        
        // When
        val exitCode = commandLine.execute("INVALID")
        
        // Then
        assertEquals(1, exitCode)
    }
    
    @Test
    fun `should handle language option`() {
        // Given
        val resourceId = "32015R1998"
        val mockNotice = createMockNotice()
        
        coEvery { cellarApiClient.fetchNotice(resourceId) } returns mockNotice
        
        // When
        val exitCode = commandLine.execute("32015R1998", "-l", "fra")
        
        // Then
        assertEquals(0, exitCode)
    }
    
    private fun createMockNotice(): Notice {
        val uri = URI(
            value = "http://publications.europa.eu/resource/celex/32015R1998",
            identifier = "32015R1998",
            type = "work"
        )
        
        val work = Work(
            _uri = uri,
            dateEntryIntoForce = listOf(
                Date(
                    type = "date",
                    value = "2016-02-01",
                    year = 2016,
                    month = 2,
                    day = 1
                )
            ),
            amendedBy = listOf(
                ResourceReference(
                    type = "amendment",
                    uri = URI(
                        value = "http://publications.europa.eu/resource/celex/32017R0815",
                        identifier = "32017R0815",
                        type = "work"
                    )
                )
            ),
            basisForActConsolidated = listOf(
                EmbeddedNotice(
                    type = "consolidation",
                    uri = URI(
                        value = "http://publications.europa.eu/resource/celex/02015R1998-20160201",
                        identifier = "02015R1998-20160201",
                        type = "work"
                    ),
                    notice = CompactNotice(
                        work = CompactWork(
                            idCelex = SimpleValue(value = "02015R1998-20160201"),
                            workDateDocument = Date(
                                type = "date",
                                value = "2016-02-01",
                                year = 2016,
                                month = 2,
                                day = 1
                            )
                        )
                    )
                )
            )
        )
        
        val expression = Expression(
            language = SimpleValue(value = "eng"),
            title = SimpleValue(value = "Commission Regulation (EU) 2015/1998 of 5 November 2015")
        )
        
        return Notice(
            decoding = "utf-8",
            type = "tree",
            work = work,
            expression = listOf(expression),
            manifestation = emptyList()
        )
    }
}
