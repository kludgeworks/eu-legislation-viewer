package eu.legislation.formex.ingester

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestClient

@SpringBootTest
@ActiveProfiles("test")
class FormexIngesterApplicationTests {

    @TestConfiguration
    class TestConfig {
        @Bean
        @Primary
        fun restClient(): RestClient = mock(RestClient::class.java)
    }

    @Test
    fun contextLoads() {
        // Verify that the Spring context loads successfully
    }
}
