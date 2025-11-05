#!/usr/bin/env elixir

# Manual test script for telemetry progress bars
# This simulates the telemetry events to verify the rendering works

Mix.install([
  {:progress_bar, "~> 3.0"}
])

defmodule FormexV4.Telemetry.MultiBarRenderer do
  @moduledoc """
  Test version of MultiBarRenderer
  """

  @bar_width 50
  @num_bars 4

  def render(state) do
    if IO.ANSI.enabled?() do
      lines = [
        render_producer_bar(state.producer),
        render_downloads_bar(state.downloads),
        render_processing_bar(state.processing),
        render_database_bar(state.database)
      ]

      IO.write([
        move_cursor_up(@num_bars),
        Enum.join(lines, "\n"),
        "\n"
      ])
    end
  end

  def initialize do
    if IO.ANSI.enabled?() do
      IO.puts(String.duplicate("\n", @num_bars - 1))
    end
  end

  def finalize do
    if IO.ANSI.enabled?() do
      IO.puts("")
    end
  end

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
    "#{IO.ANSI.clear_line()}#{stage_padded} #{bar} #{percentage_str} (#{info})"
  end

  defp move_cursor_up(n) do
    IO.ANSI.cursor_up(n)
  end
end

# Simulate progress
alias FormexV4.Telemetry.MultiBarRenderer

IO.puts("Testing multi-bar progress rendering...")
IO.puts("This will simulate a sync process with 100 files")
IO.puts("")

MultiBarRenderer.initialize()

total = 100

# Simulate producer stage
state = %{
  producer: %{current: total, total: total, label: "Diff: #{total} new"},
  downloads: %{current: 0, total: total, cached: 0},
  processing: %{current: 0, total: total, failed: 0},
  database: %{current: 0, total: total}
}

# Simulate progress
for i <- 1..total do
  # Simulate varying download speed
  downloaded = i
  # ~33% cache hit rate
  cached = div(i, 3)
  # Processing lags behind
  processed = max(0, i - 5)
  # 2 failures midway
  failed = if i > 50, do: 2, else: 0
  # Database commits in batches
  committed = max(0, i - 10)

  state = %{
    producer: %{current: total, total: total, label: "Diff: #{total} new"},
    downloads: %{current: downloaded, total: total, cached: cached},
    processing: %{current: processed, total: total, failed: failed},
    database: %{current: committed, total: total}
  }

  MultiBarRenderer.render(state)
  Process.sleep(50)
end

# Final render
state = %{
  producer: %{current: total, total: total, label: "Diff: #{total} new"},
  downloads: %{current: total, total: total, cached: 33},
  processing: %{current: total, total: total, failed: 2},
  database: %{current: total, total: total}
}

MultiBarRenderer.render(state)
MultiBarRenderer.finalize()

IO.puts("Test completed!")
