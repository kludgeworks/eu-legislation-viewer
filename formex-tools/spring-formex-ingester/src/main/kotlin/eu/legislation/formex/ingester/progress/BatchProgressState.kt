package eu.legislation.formex.ingester.progress

import kotlin.time.Duration.Companion.milliseconds

/**
 * Formats milliseconds into human-readable duration string (e.g., "1h 23m 45s", "5m 12s", "42s").
 */
fun formatDuration(milliseconds: Long): String {
    val duration = milliseconds.milliseconds
    val hours = duration.inWholeHours
    val minutes = duration.inWholeMinutes % MINUTES_PER_HOUR
    val seconds = duration.inWholeSeconds % SECONDS_PER_MINUTE

    return when {
        hours > 0 -> "%dh %dm %ds".format(hours, minutes, seconds)
        minutes > 0 -> "%dm %ds".format(minutes, seconds)
        else -> "%ds".format(seconds)
    }
}

private const val MINUTES_PER_HOUR = 60L
private const val SECONDS_PER_MINUTE = 60L

/**
 * Represents the current state of batch processing for progress visualization.
 *
 * @param smoothedItemsPerSec Exponentially smoothed speed for stable ETA calculations
 * @param skipLimit Maximum allowed skips before job fails (null if unknown)
 */
data class BatchProgressState(
    val stepName: String,
    val itemsRead: Long,
    val itemsWritten: Long,
    val itemsSkipped: Long,
    val totalItems: Long,
    val startTimeMs: Long,
    val smoothedItemsPerSec: Double = 0.0,
    val skipLimit: Long? = null,
) {
    val percentage: Int
        get() =
            if (totalItems > 0) {
                (itemsRead * PERCENT_MULTIPLIER / totalItems).toInt()
            } else {
                0
            }

    val elapsedMs: Long
        get() = System.currentTimeMillis() - startTimeMs

    /**
     * Instantaneous speed (items/second) based on total progress.
     * Used for display but not for ETA calculation to avoid wild fluctuations.
     */
    val itemsPerSec: Double
        get() =
            if (elapsedMs > 0) {
                itemsRead.toDouble() / elapsedMs.milliseconds.inWholeSeconds
            } else {
                0.0
            }

    val estimatedRemainingMs: Long
        get() {
            if (smoothedItemsPerSec <= 0) return 0L
            val remaining = totalItems - itemsRead
            return (remaining / smoothedItemsPerSec).milliseconds.inWholeMilliseconds
        }

    /**
     * Skip percentage (0.0 to 1.0) relative to skip limit.
     * Returns null if skip limit is unknown.
     */
    val skipPercentage: Double?
        get() =
            skipLimit?.let { limit ->
                if (limit > 0) itemsSkipped.toDouble() / limit else 0.0
            }

    companion object {
        private const val PERCENT_MULTIPLIER = 100
    }

    /**
     * Formats elapsed time as "44m 51s"
     */
    fun formatElapsed(): String = formatDuration(elapsedMs)

    /**
     * Formats remaining time as "~32m 15s"
     */
    fun formatRemaining(): String = "~" + formatDuration(estimatedRemainingMs)
}
