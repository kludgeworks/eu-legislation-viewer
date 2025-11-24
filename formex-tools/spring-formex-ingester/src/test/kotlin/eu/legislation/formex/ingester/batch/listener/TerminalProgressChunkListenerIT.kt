package eu.legislation.formex.ingester.batch.listener

import eu.legislation.formex.ingester.batch.PercentageBasedSkipPolicy
import eu.legislation.formex.ingester.progress.BatchProgressState
import eu.legislation.formex.ingester.progress.TerminalProgressService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.listener.JobExecutionListener
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.batch.infrastructure.item.ItemReader
import org.springframework.batch.infrastructure.item.ItemWriter
import org.springframework.batch.core.job.parameters.JobParameters
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager

/**
 * Integration test that demonstrates TerminalProgressChunkListener does not receive chunk callbacks.
 *
 * We wire a minimal one-step job named "downloadXml" that processes a few in‑memory items
 * with chunking enabled and registers TerminalProgressChunkListener as both a ChunkListener
 * and a StepExecutionListener. We also seed the JobExecutionContext with the expected
 * key ("sparql.uriCount") so that the listener's filters would otherwise allow progress updates.
 *
 */
@SpringBootTest(classes = [TerminalProgressTestApplication::class, TerminalProgressChunkListenerIT.TestJobConfig::class])
@SpringBatchTest
@TestPropertySource(properties = ["formex.job.autostart=false"])
class TerminalProgressChunkListenerIT {

    @Autowired private lateinit var jobLauncher: JobLauncher
    @Autowired @Qualifier("testJob") private lateinit var job: Job

    // TerminalProgressService is provided as a @Primary Mockito bean in TestJobConfig
    @Autowired lateinit var terminalProgressService: TerminalProgressService

    @Test
    fun `should receive progress updates from chunk events (this will currently FAIL until fixed)`() {
        // Given a job configured below; prepare utils
        // When: run the job (it will process 12 items in chunks)
        val execution = jobLauncher.run(job, JobParameters())

        // Then: job completes successfully and progress updates SHOULD be sent
        assertEquals(BatchStatus.COMPLETED, execution.status)

        // The core assertion: expect at least one progress update from TerminalProgressChunkListener
        Mockito.verify(terminalProgressService, Mockito.atLeastOnce()).updateBatchProgress(nonNullAny(BatchProgressState::class.java))
    }

    @TestConfiguration
    class TestJobConfig {
        @Bean
        @Primary
        fun mockTerminalProgressService(): TerminalProgressService = Mockito.mock(TerminalProgressService::class.java)

        @Bean(name = ["testJob"])
        fun testJob(
            jobRepository: JobRepository,
            transactionManager: PlatformTransactionManager,
            percentageBasedSkipPolicy: PercentageBasedSkipPolicy,
            terminalProgressChunkListener: TerminalProgressChunkListener,
        ): Job {
            // Simple in-memory reader over 12 integers (ensures multiple chunks)
            val items = (1..12).map { it }

            val reader = object : ItemReader<Int> {
                private val iterator = items.iterator()
                override fun read(): Int? = if (iterator.hasNext()) iterator.next() else null
            }

            val processor = ItemProcessor<Int, Int> { it } // pass-through
            val writer = ItemWriter<Int> { /* no-op */ }

            val step =
                StepBuilder("downloadXml", jobRepository)
                    .chunk<Int, Int>(5) // multiple chunks: 5,5,2
                    .transactionManager(transactionManager)
                    .reader(reader)
                    .processor(processor)
                    .writer(writer)
                    // Register explicitly as ChunkListener (new API) to receive chunk callbacks
                    .listener(terminalProgressChunkListener as org.springframework.batch.core.listener.ChunkListener<Any, Any>)
                    // Also register as StepExecutionListener so it can capture StepExecution
                    .listener(terminalProgressChunkListener)
                    // Keep skip policy available (although no errors here)
                    .listener(percentageBasedSkipPolicy)
                    .build()

            return JobBuilder("testDownloadJob", jobRepository)
                .start(step)
                .listener(object : JobExecutionListener {
                    override fun beforeJob(jobExecution: JobExecution) {
                        // Seed the expected total count so listener doesn't early-return
                        jobExecution.executionContext.putLong("sparql.uriCount", 100L)
                    }
                    override fun afterJob(jobExecution: JobExecution) { /* no-op */ }
                })
                .build()
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T> nonNullAny(type: Class<T>): T {
    ArgumentMatchers.any(type)
    return null as T
}
