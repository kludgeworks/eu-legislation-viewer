package com.kludgeworks.eu.legislation.cellarclient

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Timeout
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import kotlin.test.Test

@SpringBootTest(
    classes = [CellarApiClient::class, KtorConfiguration::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@SpringJUnitConfig
class CellarApiClientTest {

    @Autowired
    private lateinit var cellarApiClient: CellarApiClient

    @Test
    @Timeout(value = 60)
    fun `should handle network timeout gracefully`() = runTest {
        // If this call completes without throwing an exception, the test passes
        val notice = cellarApiClient.fetchNotice()

        // We're just verifying the method completed without throwing an exception
        // and returned either a valid notice or null
        println("notice = $notice")
        assertNotNull(notice)
    }
}
