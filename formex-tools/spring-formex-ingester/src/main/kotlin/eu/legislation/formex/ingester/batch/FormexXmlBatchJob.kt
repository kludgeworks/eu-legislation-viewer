package eu.legislation.formex.ingester.batch

import eu.legislation.formex.ingester.batch.listener.ErrorLoggingSkipListener
import eu.legislation.formex.ingester.batch.listener.MordantChunkListener
import eu.legislation.formex.ingester.batch.listener.MordantJobListener
import eu.legislation.formex.ingester.batch.listener.MordantStepListener
import eu.legislation.formex.ingester.batch.processor.StreamingXmlDownloadProcessor
import eu.legislation.formex.ingester.batch.processor.UriFilterProcessor
import eu.legislation.formex.ingester.batch.processor.XmlPostProcessor
import eu.legislation.formex.ingester.domain.model.CellarData
import eu.legislation.formex.ingester.domain.model.XmlMetadata
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.job.parameters.JobParametersIncrementer
import org.springframework.batch.core.listener.ExecutionContextPromotionListener
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.infrastructure.item.support.CompositeItemProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.client.RestClient
import java.time.Duration

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
        private const val CHUNK_SIZE = 10
        private const val MAX_SKIP_LIMIT = 1000

        // Thread pool configuration for parallel downloads
        private const val CORE_POOL_SIZE = 10
        private const val MAX_POOL_SIZE = 20
        private const val QUEUE_CAPACITY = 100

        // HTTP timeout configuration (SPARQL queries can take 3-4 minutes)
        private const val READ_TIMEOUT_MINUTES = 4L
    }

    /**
     * Main job bean that orchestrates the entire process.
     *
     * @param jobRepository Spring Batch job repository
     * @param fetchSparqlUrisStep Step 1 - fetches URIs from SPARQL endpoint (runs once)
     * @param downloadXmlStep Step 2 - downloads, parses, and inserts XML documents with metadata
     * @param mordantJobListener Optional job listener for CLI progress (feature flag controlled)
     * @return The configured job
     */
    @Bean
    fun processFormexXml(
        jobRepository: JobRepository,
        fetchSparqlUrisStep: Step,
        downloadXmlStep: Step,
        mordantJobListener: MordantJobListener?,
    ): Job {
        val jobBuilder =
            JobBuilder("processFormexXml", jobRepository)
                .incrementer(timestampIncrementer())
                .start(fetchSparqlUrisStep)
                .next(downloadXmlStep)

        // Add Mordant listener if available (controlled by feature flag)
        mordantJobListener?.let { jobBuilder.listener(it) }

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
     * Step 1: Fetch URIs from SPARQL endpoint and cache in JobExecutionContext.
     *
     * This step runs a Tasklet that:
     * - Queries the EU SPARQL endpoint (can take 3-4 minutes)
     * - Parses CSV response into List<CellarData>
     * - Stores result in JobExecutionContext (persisted to database)
     *
     * On restart:
     * - Tasklet detects cached data in JobExecutionContext
     * - Skips SPARQL fetch (fast restart)
     * - Downstream steps access cached data via late binding
     *
     * @param jobRepository Spring Batch job repository
     * @param transactionManager Transaction manager for step
     * @param sparqlFetchTasklet Tasklet that performs SPARQL fetch
     * @param mordantStepListener Optional step listener for CLI progress (feature flag controlled)
     * @return The configured fetch step
     */
    @Bean
    fun fetchSparqlUrisStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        sparqlFetchTasklet: SparqlUriFetchTasklet,
        mordantStepListener: MordantStepListener?,
    ): Step {
        val stepBuilder =
            StepBuilder("fetchSparqlUris", jobRepository)
                .tasklet(sparqlFetchTasklet, transactionManager)
                .listener(executionContextPromotionListener())

        // Add Mordant listener if available (controlled by feature flag)
        mordantStepListener?.let { stepBuilder.listener(it) }

        return stepBuilder.build()
    }

    /**
     * Promotes SPARQL data from step execution context to job execution context.
     * This allows subsequent steps to access the cached data.
     */
    @Bean
    fun executionContextPromotionListener(): ExecutionContextPromotionListener =
        ExecutionContextPromotionListener().apply {
            setKeys(arrayOf(SparqlUriFetchTasklet.Companion.SPARQL_DATA_KEY))
        }

    /**
     * Step 2: Download XML files, extract metadata, and insert into database.
     *
     * Reads from cached SPARQL data (injected via late binding from JobExecutionContext):
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
     * - CachedSparqlUriReader restores position from StepExecutionContext
     * - Chunks already committed are skipped (no duplicate processing)
     * - Failed chunk is retried from checkpoint
     *
     * Fault tolerance:
     * - Skip policy allows up to 1000 items to fail without stopping the job
     * - Skipped items are logged to processing_errors table via ErrorLoggingSkipListener
     * - Error records persist in a separate transaction even when chunk rolls back
     *
     * @param jobRepository Spring Batch job repository
     * @param transactionManager Transaction manager for chunk commits
     * @param reader Step-scoped reader with cached SPARQL data (returns concrete type for ItemStream detection)
     * @param filterProcessor Filters out existing URIs
     * @param downloadProcessor Downloads XML and calculates metadata
     * @param postProcessor Extracts version and root_element from XML
     * @param writer Inserts records into database
     * @param errorLoggingSkipListener Logs skipped items to database
     * @param taskExecutor Task executor for parallel chunk processing
     * @param mordantStepListener Optional step listener for CLI progress (feature flag controlled)
     * @param mordantChunkListener Optional chunk listener for CLI progress (feature flag controlled)
     * @return The configured download step
     */
    @Suppress(
        "kotlin:S107", // Spring Batch step definitions require many parameters
        "LongParameterList", // Spring Batch DI requires many parameters
        "DEPRECATION", // See: https://github.com/spring-projects/spring-batch/issues/5084
    )
    @Bean
    fun downloadXmlStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        reader: CachedSparqlUriReader,
        filterProcessor: UriFilterProcessor,
        downloadProcessor: StreamingXmlDownloadProcessor,
        postProcessor: XmlPostProcessor,
        writer: XmlDatabaseWriter,
        errorLoggingSkipListener: ErrorLoggingSkipListener,
        taskExecutor: AsyncTaskExecutor,
        mordantStepListener: MordantStepListener?,
        mordantChunkListener: MordantChunkListener?,
    ): Step {
        // Chain filter, download, and post-processing processors
        val compositeProcessor =
            CompositeItemProcessor<CellarData, XmlMetadata>().apply {
                setDelegates(listOf(filterProcessor, downloadProcessor, postProcessor))
            }

        val stepBuilder =
            StepBuilder("downloadXml", jobRepository)
                .chunk<CellarData, XmlMetadata>(CHUNK_SIZE, transactionManager)
                .reader(reader)
                .processor(compositeProcessor)
                .writer(writer)
                .taskExecutor(taskExecutor)
                .faultTolerant()
                .skip(Exception::class.java)
                .skipLimit(MAX_SKIP_LIMIT)
                .listener(errorLoggingSkipListener)

        // Add Mordant listeners if available (controlled by feature flag)
        mordantStepListener?.let { stepBuilder.listener(it) }
        mordantChunkListener?.let { stepBuilder.listener(it) }

        return stepBuilder.build()
    }

    /**
     * Task executor for parallel XML downloads.
     *
     * Configuration:
     * - Core pool: 10 threads
     * - Max pool: 20 threads
     * - Queue capacity: 100 items
     * - Thread name prefix: "xml-download-"
     *
     * @return The configured task executor
     */
    @Bean
    fun taskExecutor(): AsyncTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = CORE_POOL_SIZE
            maxPoolSize = MAX_POOL_SIZE
            queueCapacity = QUEUE_CAPACITY
            setThreadNamePrefix("xml-download-")
            initialize()
        }

    /**
     * RestClient configured for large file downloads and SPARQL queries.
     *
     * Uses JDK HttpClient (no external dependencies).
     * Timeouts configured for EU SPARQL endpoint (can take 3-4 minutes for large queries).
     *
     * @return The configured RestClient
     */
    @Bean
    fun restClient(): RestClient =
        RestClient
            .builder()
            .requestFactory(
                JdkClientHttpRequestFactory().apply {
                    setReadTimeout(Duration.ofMinutes(READ_TIMEOUT_MINUTES))
                },
            ).build()
}
