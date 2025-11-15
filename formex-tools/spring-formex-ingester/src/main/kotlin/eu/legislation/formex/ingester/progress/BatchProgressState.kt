package eu.legislation.formex.ingester.progress

import org.springframework.batch.core.BatchStatus

/**
 * Formats milliseconds into human-readable duration string (e.g., "1h 23m 45s", "5m 12s", "42s").
 */
fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    val minutes = seconds / 60
    val hours = minutes / 60

    return when {
        hours > 0 -> "%dh %dm %ds".format(hours, minutes % 60, seconds % 60)
        minutes > 0 -> "%dm %ds".format(minutes, seconds % 60)
        else -> "%ds".format(seconds)
    }
}

/**
 * Returns the appropriate color for a batch status.
 */
fun getStatusColor(status: BatchStatus) =
    when (status) {
        BatchStatus.COMPLETED -> com.github.ajalt.mordant.rendering.TextColors.green
        BatchStatus.FAILED -> com.github.ajalt.mordant.rendering.TextColors.red
        else -> com.github.ajalt.mordant.rendering.TextColors.yellow
    }

/**
 * Represents the current state of batch processing for progress visualization.
 *
 * @param smoothedItemsPerSec Exponentially smoothed speed for stable ETA calculations
 */
data class BatchProgressState(
    val stepName: String,
    val itemsRead: Long,
    val itemsWritten: Long,
    val itemsSkipped: Long,
    val totalItems: Long,
    val startTimeMs: Long,
    val smoothedItemsPerSec: Double = 0.0,
) {
    val percentage: Int
        get() = if (totalItems > 0) (itemsRead * 100 / totalItems).toInt() else 0

    val elapsedMs: Long
        get() = System.currentTimeMillis() - startTimeMs

    /**
     * Instantaneous speed (items/second) based on total progress.
     * Used for display but not for ETA calculation to avoid wild fluctuations.
     */
    val itemsPerSec: Double
        get() = if (elapsedMs > 0) itemsRead.toDouble() / (elapsedMs / 1000.0) else 0.0

    val estimatedRemainingMs: Long
        get() {
            if (smoothedItemsPerSec <= 0) return 0L
            val remaining = totalItems - itemsRead
            return (remaining / smoothedItemsPerSec * 1000).toLong()
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
