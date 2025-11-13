package eu.legislation.formex.ingester

import eu.legislation.formex.ingester.processor.StreamingXmlDownloadProcessor
import eu.legislation.formex.ingester.processor.UriFilterProcessor
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.infrastructure.item.support.CompositeItemProcessor
import org.springframework.batch.infrastructure.item.support.ListItemReader
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * Spring Batch job configuration for processing Formex XML documents.
 *
 * Job flow:
 * 1. downloadXmlStep: Fetch URIs from SPARQL, filter existing, download XML files, extract metadata, and insert
 */
@Configuration
class FormexXmlBatchJob {
    /**
     * Main job bean that orchestrates the entire process.
     *
     * @param jobRepository Spring Batch job repository
     * @param downloadXmlStep Step 1 - downloads, parses, and inserts XML documents with metadata
     * @return The configured job
     */
    @Bean
    fun processFormexXml(
        jobRepository: JobRepository,
        downloadXmlStep: Step,
    ): Job =
        JobBuilder("processFormexXml", jobRepository)
            .start(downloadXmlStep)
            .build()

    /**
     * SPARQL CSV reader bean with @StepScope.
     *
     * @StepScope ensures the reader is created per step execution, not during
     * application context loading. This defers the HTTP call to the SPARQL endpoint
     * until the step actually runs, allowing the application to start without
     * requiring the SPARQL endpoint to be available.
     *
     * Returns the concrete FlatFileItemReader type (not the interface) to allow
     * Spring Batch to detect listener annotations on the implementation class.
     *
     * @param sparqlReader Factory for creating the reader
     * @return Step-scoped FlatFileItemReader for CellarData
     */
    @Bean
    @StepScope
    fun sparqlCsvReader(sparqlReader: SparqlUriReader): ListItemReader<CellarData> =
        sparqlReader.createReader() as ListItemReader<CellarData>

    /**
     * Step 1: Download XML files, extract metadata, and insert into database.
     *
     * - Reads URIs from SPARQL endpoint (single HTTP request)
     * - Filters out existing URIs (batch database checks)
     * - Downloads XML files in parallel (streaming, no memory loading)
     * - Stores files permanently in configured storage directory
     * - Extracts version and root_element from XML documents
     * - Inserts complete records into database
     *
     * Fault tolerance:
     * - Skip policy allows up to 1000 items to fail without stopping the job
     * - Skipped items are logged to processing_errors table via ErrorLoggingSkipListener
     * - Error records persist in a separate transaction even when chunk rolls back
     *
     * @param jobRepository Spring Batch job repository
     * @param transactionManager Transaction manager for chunk commits
     * @param reader Step-scoped SPARQL CSV reader (concrete ListItemReader type)
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
        reader: ListItemReader<CellarData>,
        filterProcessor: UriFilterProcessor,
        downloadProcessor: StreamingXmlDownloadProcessor,
        postProcessor: XmlPostProcessor,
        writer: XmlDatabaseWriter,
        errorLoggingSkipListener: ErrorLoggingSkipListener,
        taskExecutor: AsyncTaskExecutor,
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
            .taskExecutor(taskExecutor)
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
     * RestClient configured for large file downloads.
     *
     * Timeouts:
     * - Connect timeout: 30 seconds
     * - Read timeout: 300 seconds (5 minutes for large files)
     *
     * @return The configured RestClient
     */
    @Bean
    fun restClient(): RestClient {
        val requestFactory =
            HttpComponentsClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofSeconds(30))
                setReadTimeout(Duration.ofMinutes(5))
            }

        return RestClient
            .builder()
            .requestFactory(requestFactory)
            .build()
    }
}
