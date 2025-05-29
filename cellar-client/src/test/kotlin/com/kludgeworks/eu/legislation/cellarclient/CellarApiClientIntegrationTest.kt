package com.kludgeworks.eu.legislation.cellarclient

import com.kludgeworks.eu.legislation.cellarclient.model.Notice // Expecting the domain Notice
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled // Keep for inspectRawXmlStructure
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDateTime

@SpringBootTest(classes = [CellarApiClient::class]) // Specify classes to load for this test
class CellarApiClientIntegrationTest {

    private val logger = LoggerFactory.getLogger(CellarApiClientIntegrationTest::class.java)

    @Autowired
    private lateinit var cellarApiClient: CellarApiClient

    private val testUuid = "ebb1a9b2-a87f-11e5-b528-01aa75ed71a1" // This is the Cellar URI for 32015R2426
    private val testLanguage = "eng"
    private val expectedWorkEli = "http://data.europa.eu/eli/reg_impl/2015/2426/oj" // From XML inspection

    @Test
    @Disabled
    fun `fetchNoticeXml should retrieve non-empty XML string`() {
        runBlocking {
            val xmlString = cellarApiClient.fetchNoticeXml(testUuid, testLanguage)
            assertNotNull(xmlString)
            assertTrue(xmlString.isNotBlank())
            logger.info("Fetched XML for UUID $testUuid (first 500 chars): ${xmlString.take(500)}")
        }
    }

    @Test
    @Disabled
    // This test will drive the implementation of the mapping logic in CellarApiClient
    fun `getNotice should fetch and parse notice from Cellar API into domain model`() {
        runBlocking {
            try {
                val notice = cellarApiClient.getNotice(testUuid, testLanguage)
                assertNotNull(notice)
                logger.info("Successfully parsed notice: $notice")

                // Assertions for Work
                val work = notice.work
                assertNotNull(work)
                assertNotNull(work.selfUri, "Work self URI should not be null")
                logger.info("Work self URI: ${work.selfUri}")

                assertEquals(expectedWorkEli, work.eli ?: work.resourceLegalEli, "Work ELI does not match")
                assertTrue(work.identifiers.any { it.contains("32015R2426") }, "Work identifiers should include CELEX")

                println("notice = ${notice}")


            } catch (e: CellarApiException) {
                logger.error("CellarApiException during getNotice test: ${e.message}", e)
                e.cause?.let { cause ->
                    logger.error("Root cause: ${cause.javaClass.name} - ${cause.message}")
                    // Forcing a more detailed print to console for immediate visibility in test runner
                    println("Root cause stack trace for CellarApiException's cause:")
                    cause.printStackTrace(System.out) // Print to System.out for easier capture from test runner
                }
                throw e
            }
        }
    }

    @Test
    @Disabled("Test for fetching raw XML to inspect its structure for mapping - run manually if needed")
    fun inspectRawXmlStructure() {
        runBlocking {
            val xmlString = cellarApiClient.fetchNoticeXml(testUuid, testLanguage)
            println("--- RAW XML for $testUuid ---")
            println(xmlString)
            println("--- END RAW XML ---")
        }
    }
}