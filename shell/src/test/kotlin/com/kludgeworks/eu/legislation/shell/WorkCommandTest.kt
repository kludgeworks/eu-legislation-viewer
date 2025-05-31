package com.kludgeworks.eu.legislation.shell

import com.kludgeworks.eu.legislation.cellarclient.models.*
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import picocli.CommandLine

class WorkCommandTest {

    private lateinit var cellarService: CellarService
    private lateinit var workCommand: WorkCommand
    private lateinit var commandLine: CommandLine

    @BeforeEach
    fun setUp() {
        cellarService = mockk()
        workCommand = WorkCommand(cellarService)
        commandLine = CommandLine(workCommand)
    }

    @Test
    fun `should display work information when resource ID is provided`() {
        // Given
        val resourceId = "32015R1998"
        val mockNotice = createMockNotice()

        coEvery { cellarService.fetchNotice(resourceId) } returns mockNotice

        // When
        val exitCode = commandLine.execute("-r", resourceId)

        // Then
        assertEquals(0, exitCode)
    }

    @Test
    fun `should display work information when CELEX number is provided`() {
        // Given
        val celexNumber = "32015R1998"
        val mockNotice = createMockNotice()

        coEvery { cellarService.fetchNoticeByCelex(celexNumber) } returns mockNotice

        // When
        val exitCode = commandLine.execute("-c", celexNumber)

        // Then
        assertEquals(0, exitCode)
    }

    @Test
    fun `should return error code when resource not found by ID`() {
        // Given
        val resourceId = "INVALID"

        coEvery { cellarService.fetchNotice(resourceId) } returns null

        // When
        val exitCode = commandLine.execute("-r", resourceId)

        // Then
        assertEquals(1, exitCode)
    }

    @Test
    fun `should return error code when resource not found by CELEX`() {
        // Given
        val celexNumber = "INVALID"

        coEvery { cellarService.fetchNoticeByCelex(celexNumber) } returns null

        // When
        val exitCode = commandLine.execute("-c", celexNumber)

        // Then
        assertEquals(1, exitCode)
    }

    @Test
    fun `should handle language option with resource ID`() {
        // Given
        val resourceId = "32015R1998"
        val mockNotice = createMockNotice()

        coEvery { cellarService.fetchNotice(resourceId) } returns mockNotice

        // When
        val exitCode = commandLine.execute("-r", resourceId, "-l", "fra")

        // Then
        assertEquals(0, exitCode)
    }

    @Test
    fun `should handle language option with CELEX number`() {
        // Given
        val celexNumber = "32015R1998"
        val mockNotice = createMockNotice()

        coEvery { cellarService.fetchNoticeByCelex(celexNumber) } returns mockNotice

        // When
        val exitCode = commandLine.execute("-c", celexNumber, "-l", "fra")

        // Then
        assertEquals(0, exitCode)
    }

    @Test
    fun `should return error when both resource ID and CELEX are provided`() {
        // When
        val exitCode = commandLine.execute("-r", "32015R1998", "-c", "32015R1998")

        // Then
        assertEquals(2, exitCode) // Picocli returns 2 for validation errors
    }

    @Test
    fun `should return error when neither resource ID nor CELEX are provided`() {
        // When
        val exitCode = commandLine.execute()

        // Then
        assertEquals(2, exitCode) // Picocli returns 2 for validation errors
    }

    private fun createMockNotice(): Notice {
        val uri = URI(
            value = "http://publications.europa.eu/resource/celex/32015R1998",
            identifier = "32015R1998",
            type = "work"
        )

        val work = Work(
            uri = uri,
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
                    ),
                    sameAs = listOf(
                        SameAs(
                            uri = URI(
                                value = "http://publications.europa.eu/resource/celex/32017R0815",
                                identifier = "32017R0815",
                                type = "celex"
                            )
                        ),
                        SameAs(
                            uri = URI(
                                value = "http://publications.europa.eu/resource/eli/reg_impl/2017/0815/oj",
                                identifier = "reg_impl:2017:0815:oj",
                                type = "eli"
                            )
                        )
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
                    ),
                    sameAs = listOf(
                        SameAs(
                            uri = URI(
                                value = "http://publications.europa.eu/resource/celex/02015R1998-20160201",
                                identifier = "02015R1998-20160201",
                                type = "celex"
                            )
                        ),
                        SameAs(
                            uri = URI(
                                value = "http://publications.europa.eu/resource/eli/reg_cons/2015/1998/20160201",
                                identifier = "reg_cons:2015:1998:20160201",
                                type = "eli"
                            )
                        )
                    )
                )
            ),
            idCelex = SimpleValue(value = "32015R1998")
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
