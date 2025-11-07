package eu.legislation.formex.ingester.config;

import eu.legislation.formex.ingester.listener.TempFileCleanupListener;
import eu.legislation.formex.ingester.model.SparqlUri;
import eu.legislation.formex.ingester.model.XmlMetadata;
import eu.legislation.formex.ingester.processor.StreamingXmlDownloadProcessor;
import eu.legislation.formex.ingester.processor.UriFilterProcessor;
import eu.legislation.formex.ingester.reader.SparqlUriReader;
import eu.legislation.formex.ingester.tasklet.XmlPostProcessor;
import eu.legislation.formex.ingester.writer.XmlDatabaseWriter;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.support.CompositeItemProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Spring Batch job configuration for processing Formex XML documents.
 * 
 * Job flow:
 * 1. downloadXmlStep: Fetch URIs from SPARQL, filter existing, download XML files in parallel
 * 2. postProcessStep: Extract version and root_element using DuckDB webbed extension
 */
@Configuration
public class FormexXmlBatchJob {
    
    /**
     * Main job bean that orchestrates the entire process.
     * 
     * @param jobRepository Spring Batch job repository
     * @param downloadXmlStep Step 1 - downloads and inserts XML documents
     * @param postProcessStep Step 2 - extracts metadata from XML
     * @return The configured job
     */
    @Bean
    public Job processFormexXml(
            JobRepository jobRepository,
            Step downloadXmlStep,
            Step postProcessStep) {
        return new JobBuilder("processFormexXml", jobRepository)
            .start(downloadXmlStep)
            .next(postProcessStep)
            .build();
    }
    
    /**
     * Step 1: Download XML files and insert into database.
     * 
     * - Reads URIs from SPARQL endpoint (single HTTP request)
     * - Filters out existing URIs (batch database checks)
     * - Downloads XML files in parallel (streaming, no memory loading)
     * - Inserts records into DuckDB
     * - Cleans up temp files after successful completion
     * 
     * @param jobRepository Spring Batch job repository
     * @param transactionManager Transaction manager for chunk commits
     * @param reader SPARQL URI reader
     * @param filterProcessor Filters out existing URIs
     * @param downloadProcessor Downloads XML and calculates metadata
     * @param writer Inserts records into database
     * @param cleanupListener Cleans up temp files after step completion
     * @return The configured download step
     */
    @Bean
    public Step downloadXmlStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            SparqlUriReader reader,
            UriFilterProcessor filterProcessor,
            StreamingXmlDownloadProcessor downloadProcessor,
            XmlDatabaseWriter writer,
            TempFileCleanupListener cleanupListener) {
        
        // Chain filter and download processors
        CompositeItemProcessor<SparqlUri, XmlMetadata> compositeProcessor = 
            new CompositeItemProcessor<>();
        compositeProcessor.setDelegates(List.of(filterProcessor, downloadProcessor));
        
        return new StepBuilder("downloadXml", jobRepository)
            .<SparqlUri, XmlMetadata>chunk(10, transactionManager) // Smaller chunks for large files
            .reader(reader)
            .processor(compositeProcessor)
            .writer(writer)
            .listener(cleanupListener)  // Cleanup temp files after step completes
            .taskExecutor(taskExecutor()) // Parallel processing
            .throttleLimit(10) // Max 10 concurrent downloads
            .build();
    }
    
    /**
     * Step 2: Post-process XML documents to extract metadata.
     * 
     * - Installs DuckDB webbed extension
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
    public Step postProcessStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            XmlPostProcessor postProcessor) {
        return new StepBuilder("postProcess", jobRepository)
            .tasklet(postProcessor, transactionManager)
            .build();
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
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("xml-download-");
        executor.initialize();
        return executor;
    }
    
    /**
     * RestTemplate configured for large file downloads.
     * 
     * Timeouts:
     * - Connect timeout: 30 seconds
     * - Read timeout: 300 seconds (5 minutes for large files)
     * 
     * @return The configured RestTemplate
     */
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate template = new RestTemplate();
        
        // Configure for large file downloads
        HttpComponentsClientHttpRequestFactory requestFactory = 
            new HttpComponentsClientHttpRequestFactory();
        requestFactory.setConnectTimeout(30000);
        requestFactory.setReadTimeout(300000); // 5 minutes for large files
        
        template.setRequestFactory(requestFactory);
        
        return template;
    }
}
