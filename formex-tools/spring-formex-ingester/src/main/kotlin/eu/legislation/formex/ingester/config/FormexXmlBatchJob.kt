package eu.legislation.formex.ingester.config

import eu.legislation.formex.ingester.listener.TempFileCleanupListener
import eu.legislation.formex.ingester.model.CellarData
import eu.legislation.formex.ingester.model.XmlMetadata
import eu.legislation.formex.ingester.processor.StreamingXmlDownloadProcessor
import eu.legislation.formex.ingester.processor.UriFilterProcessor
import eu.legislation.formex.ingester.reader.SparqlUriReader
import eu.legislation.formex.ingester.tasklet.XmlPostProcessor
import eu.legislation.formex.ingester.writer.XmlDatabaseWriter
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.batch.infrastructure.item.support.CompositeItemProcessor
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
 * 1. downloadXmlStep: Fetch URIs from SPARQL, filter existing, download XML files in parallel
 * 2. postProcessStep: Extract version and root_element from XML documents
 */
@Configuration
class FormexXmlBatchJob {

    /**
     * Main job bean that orchestrates the entire process.
     *
     * @param jobRepository Spring Batch job repository
     * @param downloadXmlStep Step 1 - downloads and inserts XML documents
     * @param postProcessStep Step 2 - extracts metadata from XML
     * @return The configured job
     */
    @Bean
    fun processFormexXml(
        jobRepository: JobRepository,
        downloadXmlStep: Step,
        postProcessStep: Step
    ): Job = JobBuilder("processFormexXml", jobRepository)
        .start(downloadXmlStep)
        .next(postProcessStep)
        .build()

    /**
     * Step 1: Download XML files and insert into database.
     *
     * - Reads URIs from SPARQL endpoint (single HTTP request)
     * - Filters out existing URIs (batch database checks)
     * - Downloads XML files in parallel (streaming, no memory loading)
     * - Inserts records into H2 database
     * - Cleans up temp files after successful completion
     *
     * @param jobRepository Spring Batch job repository
     * @param transactionManager Transaction manager for chunk commits
     * @param sparqlReader Factory for creating SPARQL URI reader
     * @param filterProcessor Filters out existing URIs
     * @param downloadProcessor Downloads XML and calculates metadata
     * @param writer Inserts records into database
     * @param cleanupListener Cleans up temp files after step completion
     * @return The configured download step
     */
    @Bean
    fun downloadXmlStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        sparqlReader: SparqlUriReader,
        filterProcessor: UriFilterProcessor,
        downloadProcessor: StreamingXmlDownloadProcessor,
        writer: XmlDatabaseWriter,
        cleanupListener: TempFileCleanupListener
    ): Step {
        // Chain filter and download processors
        val compositeProcessor = CompositeItemProcessor<CellarData, XmlMetadata>().apply {
            setDelegates(listOf<ItemProcessor<*, *>>(filterProcessor, downloadProcessor))
        }

        return StepBuilder("downloadXml", jobRepository)
            .chunk<CellarData, XmlMetadata>(10)
            .transactionManager(transactionManager)
            .reader(sparqlReader.createReader())
            .processor(compositeProcessor)
            .writer(writer)
            .listener(cleanupListener)
            .taskExecutor(taskExecutor())
            .build()
    }

    /**
     * Step 2: Post-process XML documents to extract metadata.
     *
     * - Extracts version from noNamespaceSchemaLocation attribute
     * - Extracts root element name
     * - Updates only new records (WHERE version IS NULL)
     *
     * @param jobRepository Spring Batch job repository
     * @param transactionManager Transaction manager
     * @param postProcessor Tasklet that extracts metadata
     * @return The configured post-process step
     */
    @Bean
    fun postProcessStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        postProcessor: XmlPostProcessor
    ): Step = StepBuilder("postProcess", jobRepository)
        .tasklet(postProcessor, transactionManager)
        .build()

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
    fun taskExecutor(): AsyncTaskExecutor = ThreadPoolTaskExecutor().apply {
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
        val requestFactory = HttpComponentsClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(30))
            setReadTimeout(Duration.ofMinutes(5))
        }
        
        return RestClient.builder()
            .requestFactory(requestFactory)
            .build()
    }
}
