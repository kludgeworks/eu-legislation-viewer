package com.kludgeworks.eu.legislation.formex.downloader.config

import brave.Tracing
import brave.context.slf4j.MDCScopeDecorator
import brave.propagation.CurrentTraceContext
import brave.propagation.ThreadLocalCurrentTraceContext
import io.micrometer.tracing.Tracer
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext
import io.micrometer.tracing.brave.bridge.BraveTracer
import io.micrometer.tracing.handler.DefaultTracingObservationHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configures Micrometer tracing backed by Brave so that trace/span identifiers
 * flow into SLF4J's MDC for log correlation.
 */
@Configuration
class TracingConfig {
    @Bean
    fun braveCurrentTraceContext(): CurrentTraceContext =
        ThreadLocalCurrentTraceContext
            .newBuilder()
            .addScopeDecorator(MDCScopeDecorator.get())
            .build()

    @Bean
    fun braveTracing(
        currentTraceContext: CurrentTraceContext,
    ): Tracing =
        Tracing
            .newBuilder()
            .currentTraceContext(currentTraceContext)
            .traceId128Bit(true)
            .build()

    @Bean
    fun micrometerTracer(tracing: Tracing): Tracer =
        BraveTracer(
            tracing.tracer(),
            BraveCurrentTraceContext.fromBrave(tracing.currentTraceContext()),
        )

    @Bean
    fun tracingObservationHandler(tracer: Tracer) = DefaultTracingObservationHandler(tracer)
}
