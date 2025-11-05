defmodule FormexV4.Telemetry.MultiBarRenderer do
  @moduledoc """
  Renders multiple concurrent progress bars for different pipeline stages.

  Displays four progress bars showing:
  - Producer: SPARQL fetch & diff computation
  - Downloads: File downloads (with cache hits)
  - Processing: Metadata extraction (with failures)
  - Database: Batch inserts to DuckDB
  """

  @bar_width 50
  @num_bars 4

  @doc """
  Renders the multi-bar display with current state.

  State structure:
  %{
    producer: %{current: 0, total: 0, label: ""},
    downloads: %{current: 0, total: 0, cached: 0},
    processing: %{current: 0, total: 0, failed: 0},
    database: %{current: 0, total: 0}
  }
  """
  def render(state) do
    if IO.ANSI.enabled?() do
      lines = [
        render_producer_bar(state.producer),
        render_downloads_bar(state.downloads),
        render_processing_bar(state.processing),
        render_database_bar(state.database)
      ]

      # Move cursor up and clear lines, then render
      IO.write([
        move_cursor_up(@num_bars),
        Enum.join(lines, "\n"),
        "\n"
      ])
    end
  end

  @doc """
  Initializes the display by printing empty lines.
  """
  def initialize do
    if IO.ANSI.enabled?() do
      IO.puts(String.duplicate("\n", @num_bars - 1))
    end
  end

  @doc """
  Finalizes the display by ensuring cursor is at the bottom.
  """
  def finalize do
    if IO.ANSI.enabled?() do
      IO.puts("")
    end
  end

  # Private functions

  defp render_producer_bar(%{current: current, total: total, label: label}) do
    percentage = calc_percentage(current, total)
    bar = render_bar(percentage)
    label_text = if label != "", do: " (#{label})", else: ""
    format_line("Producer", bar, percentage, "#{current}/#{total}#{label_text}")
  end

  defp render_downloads_bar(%{current: current, total: total, cached: cached}) do
    percentage = calc_percentage(current, total)
    bar = render_bar(percentage)
    cached_text = if cached > 0, do: " [#{cached} cached]", else: ""
    format_line("Downloads", bar, percentage, "#{current}/#{total}#{cached_text}")
  end

  defp render_processing_bar(%{current: current, total: total, failed: failed}) do
    percentage = calc_percentage(current, total)
    bar = render_bar(percentage)
    failed_text = if failed > 0, do: " [#{failed} failed]", else: ""
    format_line("Processing", bar, percentage, "#{current}/#{total}#{failed_text}")
  end

  defp render_database_bar(%{current: current, total: total}) do
    percentage = calc_percentage(current, total)
    bar = render_bar(percentage)
    format_line("Database", bar, percentage, "#{current}/#{total} committed")
  end

  defp calc_percentage(_current, 0), do: 0

  defp calc_percentage(current, total) do
    min(100, trunc(current * 100 / total))
  end

  defp render_bar(percentage) do
    filled = trunc(@bar_width * percentage / 100)
    empty = @bar_width - filled
    "|" <> String.duplicate("=", filled) <> String.duplicate(" ", empty) <> "|"
  end

  defp format_line(stage, bar, percentage, info) do
    stage_padded = String.pad_trailing("#{stage}:", 12)
    percentage_str = String.pad_leading("#{percentage}%", 4)

    # Clear to end of line to avoid artifacts
    "#{IO.ANSI.clear_line()}#{stage_padded} #{bar} #{percentage_str} (#{info})"
  end

  defp move_cursor_up(n) do
    IO.ANSI.cursor_up(n)
  end
end
