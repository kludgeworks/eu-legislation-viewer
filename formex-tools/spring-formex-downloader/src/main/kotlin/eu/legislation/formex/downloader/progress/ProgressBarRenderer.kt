package eu.legislation.formex.downloader.progress

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal

/**
 * Renders progress bars and rich progress displays for terminal output.
 *
 * Responsible for:
 * - Progress bar visualization with gradients
 * - Rich progress display layout
 * - Terminal capability detection
 * - Cursor control for animations
 */
internal class ProgressBarRenderer(
    private val terminal: Terminal,
) {
    companion object {
        private const val PROGRESS_BAR_WIDTH = 40
        private const val PROGRESS_DISPLAY_LINES = 9
    }

    @Volatile
    private var progressRenderedOnce: Boolean = false

    /**
     * Builds a gradient progress bar appropriate for the terminal's capabilities.
     */
    fun buildProgressBar(
        itemsRead: Long,
        totalItems: Long,
    ): String {
        val barWidth = PROGRESS_BAR_WIDTH
        val filled =
            if (totalItems > 0) {
                (itemsRead * barWidth / totalItems).toInt()
            } else {
                0
            }

        // Use gradient progress bar for modern terminals, solid color for older ones
        return if (terminal.terminalInfo.ansiLevel >= AnsiLevel.TRUECOLOR) {
            // Gradient from cyan to green for terminals with true color support
            (0 until filled).joinToString("") { i ->
                val hue = 180 - (i * 60.0 / barWidth).toInt() // Cyan (180°) to Green (120°)
                TextColors.hsv(hue, 0.8, 1.0)("█")
            } + TextColors.gray("░".repeat(barWidth - filled))
        } else {
            // Fallback: simple bright green for older terminals
            TextColors.brightGreen("█".repeat(filled)) +
                TextColors.gray("░".repeat(barWidth - filled))
        }
    }

    /**
     * Prints a simple chunk progress bar.
     * Updates the same line using terminal cursor control.
     */
    fun printSimpleProgress(
        itemsRead: Long,
        totalItems: Long,
    ) {
        if (totalItems <= 0) return

        val percentage = (itemsRead * 100.0 / totalItems).toInt()
        val barWidth = PROGRESS_BAR_WIDTH
        val filled = (itemsRead * barWidth / totalItems).toInt()
        val bar = "█".repeat(filled) + "░".repeat(barWidth - filled)

        val msg = "[$bar] $percentage% ($itemsRead/$totalItems items)"
        val centeredMsg = msg.padStart((70 + msg.length) / 2)

        // Use cursor control to update the same line
        terminal.cursor.move {
            startOfLine()
            clearLineAfterCursor()
        }
        print(centeredMsg)
        System.out.flush()
    }

    /**
     * Builds a skip limit progress bar that transitions from green to red.
     */
    private fun buildSkipProgressBar(
        itemsSkipped: Long,
        skipLimit: Long,
    ): String {
        val barWidth = 10 // Smaller bar for skip indicator
        val filled =
            if (skipLimit > 0) {
                ((itemsSkipped.toDouble() / skipLimit) * barWidth).toInt().coerceIn(0, barWidth)
            } else {
                0
            }

        // Calculate color based on skip percentage
        // Green (120°) at 0% -> Yellow (60°) at 50% -> Red (0°) at 100%
        val skipPercentage = if (skipLimit > 0) itemsSkipped.toDouble() / skipLimit else 0.0

        return if (terminal.terminalInfo.ansiLevel >= AnsiLevel.TRUECOLOR) {
            // Gradient from green to red for terminals with true color support
            (0 until filled).joinToString("") { i ->
                val hue = 120 - ((i.toDouble() / barWidth) * 120).toInt() // Green (120°) to Red (0°)
                TextColors.hsv(hue, 0.8, 1.0)("█")
            } + TextColors.gray("░".repeat(barWidth - filled))
        } else {
            // Fallback: color based on overall percentage
            val color =
                when {
                    skipPercentage < 0.5 -> TextColors.green
                    skipPercentage < 0.8 -> TextColors.yellow
                    else -> TextColors.red
                }
            color("█".repeat(filled)) + TextColors.gray("░".repeat(barWidth - filled))
        }
    }

    /**
     * Renders the rich progress display in a vertical definition list format.
     */
    fun renderRichProgressDisplay(state: BatchProgressState) {
        val progressBar = buildProgressBar(state.itemsRead, state.totalItems)

        // Build skip line with optional progress bar
        val skippedLine =
            if (state.skipLimit != null && state.skipLimit > 0) {
                val skipBar = buildSkipProgressBar(state.itemsSkipped, state.skipLimit)
                val skipColor =
                    when {
                        state.itemsSkipped == 0L -> TextColors.gray
                        (state.skipPercentage ?: 0.0) < 0.5 -> TextColors.green
                        (state.skipPercentage ?: 0.0) < 0.8 -> TextColors.yellow
                        else -> TextColors.red
                    }
                "${TextColors.cyan("Skipped:")}     ${skipColor("${state.itemsSkipped}")}/" +
                    "${TextColors.gray("${state.skipLimit}")} [$skipBar]"
            } else {
                "${TextColors.cyan("Skipped:")}     ${if (state.itemsSkipped > 0) {
                    TextColors.yellow("${state.itemsSkipped}")
                } else {
                    TextColors.gray("${state.itemsSkipped}")
                }}"
            }

        // Build the display content with tasteful color highlighting
        val lines =
            listOf(
                "${TextColors.cyan("Step:")}        ${TextColors.white(state.stepName)}",
                "${TextColors.cyan(
                    "Progress:",
                )}    ${TextColors.brightGreen(
                    "[",
                )}$progressBar${TextColors.brightGreen("]")} ${TextColors.brightWhite("${state.percentage}%")}",
                "${TextColors.cyan("Items:")}       ${TextColors.white("${state.itemsRead}")}" +
                    "/${TextColors.gray("${state.totalItems}")}",
                "${TextColors.cyan("Written:")}     ${TextColors.green("${state.itemsWritten}")}",
                skippedLine,
                "${TextColors.cyan("Speed:")}       " +
                    "${TextColors.brightCyan("%.1f".format(state.smoothedItemsPerSec))} " +
                    TextColors.gray("items/s"),
                "${TextColors.cyan("Elapsed:")}     ${TextColors.white(state.formatElapsed())}",
                "${TextColors.cyan("Remaining:")}   ${TextColors.brightBlue(state.formatRemaining())}",
                TextColors.green("●"),
            )

        // Clear previous display and render new one
        synchronized(terminal) {
            // Move cursor up to overwrite previous display (if this isn't the first render)
            if (progressRenderedOnce) {
                terminal.cursor.move {
                    up(PROGRESS_DISPLAY_LINES)
                }
            }

            // Print all lines
            lines.forEach { line ->
                terminal.cursor.move {
                    startOfLine()
                    clearLineAfterCursor()
                }
                terminal.println(line)
            }

            // Mark that we've rendered at least once
            progressRenderedOnce = true
        }
    }

    /**
     * Resets the render state (called when starting new progress display).
     */
    fun resetRenderState() {
        progressRenderedOnce = false
    }
}
