package com.kludgeworks.eu.legislation.formex.downloader.config

import jakarta.jms.ConnectionFactory
import org.mockito.Mockito
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.jms.core.JmsTemplate

/**
 * Provides lightweight JMS beans for the "test" profile so SpringBootTest contexts
 * do not require an embedded Artemis broker.
 */
@Configuration
@Profile("test")
class TestJmsConfiguration {
    @Bean
    @Primary
    fun testConnectionFactory(): ConnectionFactory = Mockito.mock(ConnectionFactory::class.java)

    @Bean
    @Primary
    fun testJmsTemplate(connectionFactory: ConnectionFactory): JmsTemplate = JmsTemplate(connectionFactory)
}
