package eu.legislation.formex.ingester.config

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration for Spring Batch 6.0 observability and metrics collection.
 *
 * Spring Batch 6 requires an explicit ObservationRegistry bean configured
 * in the application context to collect batch metrics and observations.
 * This replaces the deprecated usage of Micrometer's global static meter registry.
 *
 * @see <a href="https://github.com/spring-projects/spring-batch/wiki/Spring-Batch-6.0-Migration-Guide#observability-changes">Spring Batch 6.0 Migration Guide</a>
 */
@Configuration
class BatchConfig {
    /**
     * Configures the observation registry for Spring Batch metrics.
     *
     * The observation registry integrates with Micrometer to collect
     * batch job and step execution metrics.
     *
     * @param meterRegistry The meter registry to collect metrics
     * @return Configured observation registry
     */
    @Bean
    fun observationRegistry(
        meterRegistry: MeterRegistry,
        tracingObservationHandlerProvider: ObjectProvider<ObservationHandler<out Observation.Context>>,
    ): ObservationRegistry {
        val observationRegistry = ObservationRegistry.create()
        val observationConfig = observationRegistry.observationConfig()
        observationConfig.observationHandler(DefaultMeterObservationHandler(meterRegistry))
        tracingObservationHandlerProvider.ifAvailable { handler ->
            @Suppress("UNCHECKED_CAST")
            observationConfig.observationHandler(handler as ObservationHandler<Observation.Context>)
        }
        return observationRegistry
    }

    /**
     * Provides a simple in-memory meter registry.
     *
     * For production use, consider replacing SimpleMeterRegistry with
     * a production-grade registry like:
     * - PrometheusMeterRegistry (for Prometheus metrics)
     * - CompositeMeterRegistry (for multiple backends)
     *
     * @return Simple meter registry for metrics collection
     */
    @Bean
    fun meterRegistry(): SimpleMeterRegistry = SimpleMeterRegistry()
}
