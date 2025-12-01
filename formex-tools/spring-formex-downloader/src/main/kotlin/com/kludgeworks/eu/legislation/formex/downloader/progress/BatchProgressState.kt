package com.kludgeworks.eu.legislation.formex.downloader.progress

/**
 * Formats milliseconds into human-readable duration string (e.g., "1h 23m 45s", "5m 12s", "42s").
 */
fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return when {
        hours > 0 -> "%dh %dm %ds".format(hours, minutes, secs)
        minutes > 0 -> "%dm %ds".format(minutes, secs)
        else -> "%ds".format(secs)
    }
}

/**
 * Represents the current state of batch processing for progress visualization.
 *
 * Simplified version without ETA calculations or speed smoothing.
 */
data class BatchProgressState(
    val stepName: String,
    val itemsRead: Long,
    val itemsWritten: Long,
    val itemsSkipped: Long,
    val totalItems: Long,
    val startTimeMs: Long,
    val skipLimit: Long? = null,
)
