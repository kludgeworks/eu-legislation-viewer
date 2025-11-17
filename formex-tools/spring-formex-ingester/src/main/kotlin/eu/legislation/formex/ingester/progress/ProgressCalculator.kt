package eu.legislation.formex.ingester.progress

/**
 * Calculates progress metrics using Exponential Moving Average (EMA) for stable speed estimates.
 *
 * Responsible for:
 * - Speed calculation with EMA smoothing
 * - Tracking progress state between updates
 * - Providing stable estimates for ETA calculations
 */
internal class ProgressCalculator {
    companion object {
        // EMA smoothing for speed calculations
        // Alpha = 0.3 provides good balance between responsiveness and stability
        // Higher alpha (vs 0.15) makes progress bar more responsive to speed changes
        // while still smoothing out chunk-to-chunk variance
        private const val SPEED_SMOOTHING_ALPHA = 0.3
    }

    @Volatile
    private var smoothedSpeed: Double = 0.0

    @Volatile
    private var lastUpdateTimeMs: Long = 0L

    @Volatile
    private var lastItemsRead: Long = 0L

    /**
     * Updates progress state with smoothed speed calculation.
     *
     * Uses Exponential Moving Average (EMA) to smooth speed calculations:
     * - Formula: smoothed = alpha × current + (1 - alpha) × previous
     * - Alpha = 0.3 balances responsiveness with stability
     * - Higher alpha makes progress bar more responsive to real speed changes
     *
     * @param state Current progress state
     * @return Updated state with smoothed speed
     */
    fun updateWithSmoothedSpeed(state: BatchProgressState): BatchProgressState {
        val currentTimeMs = System.currentTimeMillis()

        // Calculate instantaneous speed for this update
        val currentSpeed =
            if (lastUpdateTimeMs in 1..<currentTimeMs) {
                val timeDeltaSeconds = (currentTimeMs - lastUpdateTimeMs) / 1000.0
                val itemsDelta = state.itemsRead - lastItemsRead
                if (timeDeltaSeconds > 0) itemsDelta / timeDeltaSeconds else 0.0
            } else {
                // First update - use overall average as initial estimate
                state.itemsPerSec
            }

        // Apply Exponential Moving Average (EMA) smoothing
        smoothedSpeed =
            if (smoothedSpeed == 0.0) {
                // First update - use current speed as initial value
                currentSpeed
            } else {
                SPEED_SMOOTHING_ALPHA * currentSpeed + (1 - SPEED_SMOOTHING_ALPHA) * smoothedSpeed
            }

        // Track for next update
        lastUpdateTimeMs = currentTimeMs
        lastItemsRead = state.itemsRead

        // Return updated state with smoothed speed
        return state.copy(smoothedItemsPerSec = smoothedSpeed)
    }

    /**
     * Resets calculator state (called when starting new progress tracking).
     */
    fun reset() {
        smoothedSpeed = 0.0
        lastUpdateTimeMs = 0L
        lastItemsRead = 0L
    }
}
