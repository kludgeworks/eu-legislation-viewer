package com.kludgeworks.eu.legislation.formex.downloader.batch

import com.kludgeworks.eu.legislation.formex.downloader.batch.listener.ErrorLoggingSkipListener
import com.kludgeworks.eu.legislation.formex.downloader.batch.listener.TerminalProgressChunkListener
import com.kludgeworks.eu.legislation.formex.downloader.batch.listener.TerminalProgressJobListener
import com.kludgeworks.eu.legislation.formex.downloader.batch.listener.TerminalProgressStepListener
import com.kludgeworks.eu.legislation.formex.downloader.batch.processor.HttpDownloadException
import com.kludgeworks.eu.legislation.formex.downloader.batch.processor.UriFilterProcessor
import com.kludgeworks.eu.legislation.formex.downloader.batch.processor.XmlDownloadProcessor
import com.kludgeworks.eu.legislation.formex.downloader.batch.processor.XmlPostProcessor
import com.kludgeworks.eu.legislation.formex.downloader.domain.model.CellarData
import com.kludgeworks.eu.legislation.formex.downloader.domain.model.XmlMetadata
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.job.parameters.JobParametersIncrementer
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.infrastructure.item.support.CompositeItemProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.retry.RetryPolicy
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.core.task.support.TaskExecutorAdapter
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.client.RestClient
import java.time.Duration
import java.util.concurrent.Executors

/**
 * Spring Batch job configuration for processing Formex XML documents.
 *
 * Job flow (idiomatic two-step pattern for expensive data fetching):
 * 1. fetchSparqlUrisStep: Fetch URIs from SPARQL endpoint once, cache in JobExecutionContext
 * 2. downloadXmlStep: Read from cached URIs, filter existing, download XML files, extract metadata, insert
 *
 * Restart behavior:
 * - Step 1: On restart, detects cached data and skips re-fetching (fast)
 * - Step 2: On restart, resumes from checkpoint (CachedSparqlUriReader restores position)
 */
@Configuration
class FormexXmlBatchJob {
    companion object {
        // Chunk processing configuration
        // Larger chunk size (200) reduces database transaction overhead and improves throughput
        private const val CHUNK_SIZE = 200

        // HTTP timeout configuration (SPARQL queries can take 3-4 minutes)
        private const val READ_TIMEOUT_MINUTES = 4L

        // HTTP timeout for XML downloads (shorter than SPARQL)
        private const val XML_READ_TIMEOUT_SECONDS = 30L
    }

    /**
     * Main job bean that orchestrates the entire process.
     *
     * @param jobRepository Spring Batch job repository
     * @param fetchSparqlUrisStep Step 1 - fetches URIs from SPARQL endpoint (runs once)
     * @param downloadXmlStep Step 2 - downloads, parses, and inserts XML documents with metadata
     * @param terminalProgressJobListener Optional job listener for CLI progress (feature flag controlled)
     * @return The configured job
     */
    @Bean
    fun processFormexXml(
        jobRepository: JobRepository,
        fetchSparqlUrisStep: Step,
        downloadXmlStep: Step,
        terminalProgressJobListener: TerminalProgressJobListener?,
    ): Job {
        val jobBuilder =
            JobBuilder("processFormexXml", jobRepository)
                .incrementer(timestampIncrementer())
                .start(fetchSparqlUrisStep)
                .next(downloadXmlStep)

        // Add terminal progress listener if available (controlled by feature flag)
        terminalProgressJobListener?.let { jobBuilder.listener(it) }

        return jobBuilder.build()
    }

    /**
     * Job parameters incrementer that adds a timestamp to make each run unique.
     * This allows the job to be run multiple times (Spring Batch prevents duplicate runs with same parameters).
     */
    private fun timestampIncrementer() =
        JobParametersIncrementer { parameters ->
            JobParametersBuilder(parameters)
                .addLong("run.timestamp", System.currentTimeMillis())
                .toJobParameters()
        }

    /**
     * Step 1: Fetch URIs from SPARQL endpoint and publish to JMS queue.
     *
     * This step runs a Tasklet that:
     * - Queries the EU SPARQL endpoint (can take 3-4 minutes)
     * - Parses CSV response into List<CellarData>
     * - Publishes each CellarData to persistent JMS queue
     *
     * Benefits over execution context storage:
     * - Zero execution context bloat (stores only count vs 154 MB list)
     * - Fast restart (messages remain in queue)
     * - Automatic message persistence
     *
     * On restart (idempotent):
     * - Checks "sparql.publishingCompleted" flag in execution context
     * - If flag exists, skips SPARQL fetch and publishing (messages already in queue)
     * - If flag missing, re-runs SPARQL fetch and publishes messages
     * - Prevents duplicate message publishing on restart
     *
     * @param jobRepository Spring Batch job repository
     * @param transactionManager Transaction manager for step
     * @param sparqlUriJmsPublisher Tasklet that performs SPARQL fetch and JMS publishing
     * @param terminalProgressStepListener Optional step listener for CLI progress (feature flag controlled)
     * @return The configured fetch step
     */
    @Bean
    fun fetchSparqlUrisStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        sparqlUriJmsPublisher: SparqlUriJmsPublisher,
        terminalProgressStepListener: TerminalProgressStepListener?,
    ): Step {
        val stepBuilder =
            StepBuilder("fetchSparqlUris", jobRepository)
                .tasklet(sparqlUriJmsPublisher, transactionManager)

        // Add terminal progress listener if available (controlled by feature flag)
        terminalProgressStepListener?.let { stepBuilder.listener(it) }

        return stepBuilder.build()
    }

    /**
     * Step 2: Download XML files, extract metadata, and insert into database.
     *
     * Reads from JMS queue (populated by step 1):
     * - Filters out existing URIs (batch database checks)
     * - Downloads XML files in parallel with 10-20 concurrent threads (streaming, no memory loading)
     * - Stores files permanently in configured storage directory
     * - Extracts version and root_element from XML documents
     * - Inserts complete records into database
     *
     * Parallelism:
     * - Chunk size: 10 items per chunk
     * - Thread pool: 10 core threads, 20 max threads
     * - Up to 10-20 concurrent downloads depending on load
     *
     * Restart behavior:
     * - JMS automatically redelivers unacknowledged messages
     * - Chunks already committed are acknowledged (no duplicate processing)
     * - Failed chunk messages remain in queue for retry
     *
     * Fault tolerance:
     * - Skip limit calculated dynamically as percentage of total URIs (default: 0.5%)
     * - Minimum skip limit of 10 to prevent overly aggressive failure on small datasets
     * - Skipped items are logged to processing_errors table via ErrorLoggingSkipListener
     * - Error records persist in a separate transaction even when chunk rolls back
     *
     * @param jobRepository Spring Batch job repository
     * @param transactionManager Transaction manager for chunk commits
     * @param reader JMS-based reader consuming from queue (no ItemStream needed - JMS manages state)
     * @param filterProcessor Filters out existing URIs
     * @param downloadProcessor Downloads XML and calculates metadata
     * @param postProcessor Extracts version and root_element from XML
     * @param writer Inserts records into database
     * @param errorLoggingSkipListener Logs skipped items to database
     * @param taskExecutor Task executor for parallel chunk processing
     * @param percentageBasedSkipPolicy Custom skip policy that enforces percentage-based skip limit
     * @param terminalProgressStepListener Optional step listener for CLI progress (feature flag controlled)
     * @param terminalProgressChunkListener Optional chunk listener for CLI progress (feature flag controlled)
     * @return The configured download step
     */
    @Suppress(
        "kotlin:S107", // Spring Batch step definitions require many parameters
        "LongParameterList", // Spring Batch DI requires many parameters
    )
    @Bean
    fun downloadXmlStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        reader: JmsCellarDataReader,
        filterProcessor: UriFilterProcessor,
        downloadProcessor: XmlDownloadProcessor,
        postProcessor: XmlPostProcessor,
        writer: XmlDatabaseWriter,
        errorLoggingSkipListener: ErrorLoggingSkipListener,
        taskExecutor: AsyncTaskExecutor,
        percentageBasedSkipPolicy: PercentageBasedSkipPolicy,
        terminalProgressStepListener: TerminalProgressStepListener?,
        terminalProgressChunkListener: TerminalProgressChunkListener?,
    ): Step {
        // Chain filter, download, and post-processing processors
        val compositeProcessor =
            CompositeItemProcessor<CellarData, XmlMetadata>().apply {
                setDelegates(listOf(filterProcessor, downloadProcessor, postProcessor))
            }

        val retryPolicy =
            RetryPolicy
                .builder()
                .maxRetries(3)
                .includes(Exception::class.java)
                .excludes(ExposedSQLException::class.java, HttpDownloadException::class.java)
                .build()

        // Log listener availability
        KotlinLogging.logger {}.info {
            "downloadXmlStep: terminalProgressStepListener=${terminalProgressStepListener != null}, " +
            "terminalProgressChunkListener=${terminalProgressChunkListener != null}"
        }

        val chunkBuilder =
            StepBuilder("downloadXml", jobRepository)
                .chunk<CellarData, XmlMetadata>(CHUNK_SIZE)
                .transactionManager(transactionManager)
                .reader(reader)
                .processor(compositeProcessor)
                .writer(writer)

        // Register ChunkListener before taskExecutor and faultTolerant configuration
        terminalProgressChunkListener?.let {
            KotlinLogging.logger {}.info {
                "Registering TerminalProgressChunkListener with step BEFORE taskExecutor"
            }
            chunkBuilder.listener(it)
        }

        chunkBuilder
                .taskExecutor(taskExecutor)
                .faultTolerant()
                .retryPolicy(retryPolicy)
                .skipPolicy(percentageBasedSkipPolicy)
                .listener(errorLoggingSkipListener)
                .listener(percentageBasedSkipPolicy)

        // Register StepExecutionListener
        chunkBuilder.listener(terminalProgressChunkListener)

        return chunkBuilder.build()
    }

    /**
     * Task executor for parallel XML downloads using virtual threads.
     *
     * Virtual threads (Project Loom) provide:
     * - Massive scalability: Can handle thousands of concurrent downloads
     * - Near-zero memory overhead: ~1KB per thread vs ~2MB for platform threads
     * - No queue capacity limits: No rejected execution exceptions
     * - Simplified configuration: No need to tune pool sizes
     *
     * Perfect for I/O-bound workloads like HTTP downloads.
     *
     * @return Virtual thread executor
     */
    @Bean
    fun taskExecutor(): AsyncTaskExecutor =
        TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor())

    /**
     * RestClient configured for SPARQL queries.
     *
     * Uses JDK HttpClient (no external dependencies).
     * Timeouts configured for EU SPARQL endpoint (can take 3-4 minutes for large queries).
     *
     * @return The configured RestClient for SPARQL queries
     */
    @Bean
    fun sparqlRestClient(): RestClient =
        RestClient
            .builder()
            .requestFactory(
                JdkClientHttpRequestFactory().apply {
                    setReadTimeout(Duration.ofMinutes(READ_TIMEOUT_MINUTES))
                },
            ).build()

    /**
     * RestClient configured for XML file downloads.
     *
     * Uses JDK HttpClient (no external dependencies).
     * Shorter read timeout than SPARQL client since XML downloads should be fast.
     * Note: JdkClientHttpRequestFactory uses JDK HttpClient's default connect timeout.
     *
     * @return The configured RestClient for XML downloads
     */
    @Bean
    fun xmlDownloadRestClient(): RestClient =
        RestClient
            .builder()
            .requestFactory(
                JdkClientHttpRequestFactory().apply {
                    setReadTimeout(Duration.ofSeconds(XML_READ_TIMEOUT_SECONDS))
                },
            ).build()
}
