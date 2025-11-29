package com.kludgeworks.eu.legislation.formex.downloader.observability

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks Observation lifecycles for individual Formex items.
 *
 * Each URI gets a root observation (formex.item) that spans the JMS read to DB write.
 * Stage-specific child observations (formex.<stage>) are created on demand.
 */
@Component
class ItemObservationTracker(
    private val observationRegistry: ObservationRegistry,
) {
    private val activeObservations = ConcurrentHashMap<String, Observation>()

    private fun ensureObservation(uri: String): Observation =
        activeObservations.computeIfAbsent(uri) { createRootObservation(uri) }

    private fun createRootObservation(uri: String): Observation =
        Observation
            .start("formex.item", observationRegistry)
            .lowCardinalityKeyValue("uri", uri)

    /**
     * Runs the given block inside a child observation for the provided stage.
     */
    fun <T> observeStage(
        uri: String,
        stage: String,
        block: () -> T,
    ): T {
        val parent = ensureObservation(uri)
        val childObservation =
            Observation
                .createNotStarted("formex.$stage", observationRegistry)
                .contextualName("formex.$stage")
                .lowCardinalityKeyValue("uri", uri)
                .parentObservation(parent)
        val started = childObservation.start()
        return try {
            val result =
                started.openScope().use {
                    block()
                }
            result
        } catch (ex: Throwable) {
            started.error(ex)
            throw ex
        } finally {
            started.stop()
        }
    }

    /**
     * Marks the observation complete (e.g., after successful write or filtered duplicate).
     */
    fun complete(uri: String) {
        activeObservations.remove(uri)?.stop()
    }

    /**
     * Marks the observation as failed and closes it.
     */
    fun fail(
        uri: String,
        throwable: Throwable,
    ) {
        activeObservations.remove(uri)?.apply {
            error(throwable)
            stop()
        }
    }
}
