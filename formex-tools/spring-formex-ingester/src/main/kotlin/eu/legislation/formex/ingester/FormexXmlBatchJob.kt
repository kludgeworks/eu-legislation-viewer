package eu.legislation.formex.ingester

import eu.legislation.formex.ingester.processor.StreamingXmlDownloadProcessor
import eu.legislation.formex.ingester.processor.UriFilterProcessor
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
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.client.RestClient

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
    /**
     * Main job bean that orchestrates the entire process.
     *
     * @param jobRepository Spring Batch job repository
     * @param fetchSparqlUrisStep Step 1 - fetches URIs from SPARQL endpoint (runs once)
     * @param downloadXmlStep Step 2 - downloads, parses, and inserts XML documents with metadata
     * @return The configured job
     */
    @Bean
    fun processFormexXml(
        jobRepository: JobRepository,
        fetchSparqlUrisStep: Step,
        downloadXmlStep: Step,
    ): Job =
        JobBuilder("processFormexXml", jobRepository)
            .incrementer(timestampIncrementer())
            .start(fetchSparqlUrisStep)
            .next(downloadXmlStep)
            .build()

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
     * @return The configured fetch step
     */
    @Bean
    fun fetchSparqlUrisStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        sparqlFetchTasklet: SparqlUriFetchTasklet,
    ): Step =
        StepBuilder("fetchSparqlUris", jobRepository)
            .tasklet(sparqlFetchTasklet, transactionManager)
            .build()

    /**
     * Step 2: Download XML files, extract metadata, and insert into database.
     *
     * Reads from cached SPARQL data (injected via late binding from JobExecutionContext):
     * - Filters out existing URIs (batch database checks)
     * - Downloads XML files in parallel (streaming, no memory loading)
     * - Stores files permanently in configured storage directory
     * - Extracts version and root_element from XML documents
     * - Inserts complete records into database
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
     * @return The configured download step
     */
    @Suppress(
        "kotlin:S107", // Spring Batch step definitions require many parameters
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
    ): Step {
        // Chain filter, download, and post-processing processors
        val compositeProcessor =
            CompositeItemProcessor<CellarData, XmlMetadata>().apply {
                setDelegates(listOf(filterProcessor, downloadProcessor, postProcessor))
            }

        return StepBuilder("downloadXml", jobRepository)
            .chunk<CellarData, XmlMetadata>(10, transactionManager)
            .reader(reader)
            .processor(compositeProcessor)
            .writer(writer)
            .faultTolerant()
            .skip(Exception::class.java)
            .skipLimit(1000)
            .listener(errorLoggingSkipListener)
            .build()
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
            corePoolSize = 10
            maxPoolSize = 20
            queueCapacity = 100
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
                org.springframework.http.client.JdkClientHttpRequestFactory().apply {
                    setReadTimeout(java.time.Duration.ofMinutes(4))
                },
            ).build()
}
