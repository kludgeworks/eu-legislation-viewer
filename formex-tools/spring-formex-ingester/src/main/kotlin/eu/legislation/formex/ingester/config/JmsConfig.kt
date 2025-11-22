package eu.legislation.formex.ingester.config

import jakarta.jms.ConnectionFactory
import jakarta.jms.Session
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jms.core.JmsTemplate

/**
 * Ensures application JmsTemplate uses transacted sessions.
 */
@Configuration
class JmsConfig {
    @Bean
    fun jmsTemplate(connectionFactory: ConnectionFactory): JmsTemplate =
        JmsTemplate(connectionFactory).apply {
            isSessionTransacted = true
            sessionAcknowledgeMode = Session.SESSION_TRANSACTED
        }
}
