package com.kludgeworks.eu.legislation.formex.downloader.config

import com.kludgeworks.eu.legislation.formex.downloader.observability.ItemObservationTracker
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import io.micrometer.tracing.Tracer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig

@SpringJUnitConfig(
    classes = [
        BatchConfig::class,
        TracingConfig::class,
        ItemObservationTracker::class,
    ],
)
class TracingConfigTest {
    @Autowired
    private lateinit var observationRegistry: ObservationRegistry

    @Autowired
    private lateinit var tracer: Tracer

    @Autowired
    private lateinit var itemObservationTracker: ItemObservationTracker

    @AfterEach
    fun clearMdc() {
        MDC.clear()
    }

    @Test
    fun `observation populates MDC trace identifiers`() {
        assertThat(MDC.get("traceId")).isNull()
        val observation = Observation.start("test.observation", observationRegistry)
        observation.openScope().use {
            assertThat(MDC.get("traceId")).isNotBlank
            assertThat(MDC.get("spanId")).isNotBlank
            assertThat(tracer.currentSpan()).isNotNull
        }
        observation.stop()
        assertThat(MDC.get("traceId")).isNull()
        assertThat(MDC.get("spanId")).isNull()
        assertThat(tracer.currentSpan()).isNull()
    }

    @Test
    fun `item observation tracker propagates tracing context`() {
        val uri = "http://example.test/doc/1"
        val traceId =
            itemObservationTracker.observeStage(uri, "download") {
                val traceIdInMdc = MDC.get("traceId")
                assertThat(traceIdInMdc).isNotNull()
                val currentSpan = tracer.currentSpan()
                assertThat(currentSpan).isNotNull
                currentSpan!!.context().traceId()
            }

        itemObservationTracker.fail(uri, IllegalStateException("expected failure"))

        assertThat(traceId).isNotNull()
        assertThat(MDC.get("traceId")).isNull()
        assertThat(tracer.currentSpan()).isNull()
    }
}
