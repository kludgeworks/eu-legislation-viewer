package com.kludgeworks.eu.legislation.formex.downloader

import com.kludgeworks.eu.legislation.formex.downloader.domain.CellarData
import com.kludgeworks.eu.legislation.formex.downloader.domain.ProcessingErrors
import org.apache.camel.CamelContext
import org.apache.camel.ProducerTemplate
import org.apache.camel.builder.AdviceWith
import org.apache.camel.test.spring.junit5.CamelSpringBootTest
import org.apache.camel.test.spring.junit5.UseAdviceWith
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@CamelSpringBootTest
@SpringBootTest
@ActiveProfiles("test")
@UseAdviceWith
class FormexErrorLoggingTest {

    @Autowired
    private lateinit var camelContext: CamelContext

    @Autowired
    private lateinit var producerTemplate: ProducerTemplate

    @Autowired
    private lateinit var database: Database

    @BeforeEach
    fun setUp() {
        transaction(database) {
            ProcessingErrors.deleteAll()
        }
    }

    @AfterEach
    fun tearDown() {
        camelContext.stop()
    }

    @Test
    fun `logs processing errors and continues route`() {
        AdviceWith.adviceWith(camelContext, "sparql-fetch") {
            it.replaceFromWith("direct:trigger")
        }

        AdviceWith.adviceWith(camelContext, "xml-download") {
            it.weaveAddFirst().process { throw IllegalStateException("boom") }
        }

        camelContext.start()

        val item =
            CellarData(
                item = "http://example.com/doc.xml",
                workId = "work-1",
                expressionId = "expr-1",
                manifestationId = "man-1",
                contentStreamId = "cs-1",
                language = "eng",
                mimeType = "application/xml",
            )

        producerTemplate.sendBody("direct:processItem", item)

        val errors =
            transaction(database) {
                ProcessingErrors.selectAll().toList()
            }

        assertEquals(1, errors.size)
        assertEquals(item.item, errors.first()[ProcessingErrors.uri])
        assertEquals(IllegalStateException::class.qualifiedName, errors.first()[ProcessingErrors.errorType])
    }
}
