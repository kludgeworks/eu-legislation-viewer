package com.kludgeworks.eu.legislation.formex.downloader.batch.listener

import com.kludgeworks.eu.legislation.formex.downloader.batch.PercentageBasedSkipPolicy
import com.kludgeworks.eu.legislation.formex.downloader.config.BatchProperties
import com.kludgeworks.eu.legislation.formex.downloader.progress.TerminalProgressService
import org.mockito.Mockito
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.repository.support.ResourcelessJobRepository
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager
import org.springframework.boot.SpringBootConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.transaction.PlatformTransactionManager

/**
 * Minimal Spring Boot configuration that provides in-memory Spring Batch infrastructure
 * so the listener can be exercised without the full production application context.
 */
@SpringBootConfiguration
class TerminalProgressTestApplication {

    // --- Batch infrastructure -------------------------------------------------

    @Bean
    fun transactionManager(): PlatformTransactionManager = ResourcelessTransactionManager()

    @Bean
    fun jobRepository(): JobRepository = ResourcelessJobRepository()

    @Bean
    fun jobOperator(): JobOperator = Mockito.mock(JobOperator::class.java)

    // --- Beans under test -----------------------------------------------------

    @Bean
    fun batchProperties(): BatchProperties = BatchProperties(skipPercentage = 5.0)

    @Bean
    fun percentageBasedSkipPolicy(batchProperties: BatchProperties): PercentageBasedSkipPolicy =
        PercentageBasedSkipPolicy(batchProperties)

    @Bean
    fun terminalProgressChunkListener(
        terminalProgressService: TerminalProgressService?,
    ): TerminalProgressChunkListener =
        TerminalProgressChunkListener(terminalProgressService)
}
