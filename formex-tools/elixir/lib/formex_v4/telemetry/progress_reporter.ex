defmodule FormexV4.Telemetry.ProgressReporter do
  @moduledoc """
  Telemetry handler that maintains state and renders multi-stage progress bars.

  Attaches to Broadway telemetry events and custom FormexV4 events to track:
  - Producer stage (SPARQL fetch & diff computation)
  - Downloads stage (file downloads with cache hits)
  - Processing stage (metadata extraction with failures)
  - Database stage (batch inserts)

  Uses an Agent to maintain state and throttles rendering to avoid terminal flicker.
  """

  use GenServer
  alias FormexV4.Telemetry.MultiBarRenderer

  @update_interval_ms 100

  # Client API

  @doc """
  Starts the progress reporter.

  Options:
  - :enabled - Whether to enable progress bars (default: true if TTY)
  """
  def start_link(opts \\ []) do
    enabled = Keyword.get(opts, :enabled, IO.ANSI.enabled?())
    GenServer.start_link(__MODULE__, enabled, name: __MODULE__)
  end

  @doc """
  Stops the progress reporter and finalizes display.
  """
  def stop do
    if Process.whereis(__MODULE__) do
      GenServer.stop(__MODULE__)
    end
  end

  # Server Callbacks

  @impl true
  def init(enabled) do
    if enabled do
      attach_telemetry_handlers()
      MultiBarRenderer.initialize()

      state = %{
        enabled: true,
        producer: %{current: 0, total: 0, label: ""},
        downloads: %{current: 0, total: 0, cached: 0},
        processing: %{current: 0, total: 0, failed: 0},
        database: %{current: 0, total: 0},
        last_render: 0
      }

      {:ok, state}
    else
      {:ok, %{enabled: false}}
    end
  end

  @impl true
  def handle_info({:telemetry_event, event_name, measurements, metadata}, state) do
    if state.enabled do
      new_state = handle_telemetry(event_name, measurements, metadata, state)
      new_state = maybe_render(new_state)
      {:noreply, new_state}
    else
      {:noreply, state}
    end
  end

  @impl true
  def terminate(_reason, state) do
    if state.enabled do
      MultiBarRenderer.finalize()
      detach_telemetry_handlers()
    end

    :ok
  end

  # Private Functions

  defp attach_telemetry_handlers do
    events = [
      [:formex_v4, :producer, :start],
      [:formex_v4, :producer, :sparql_complete],
      [:formex_v4, :producer, :diff_complete],
      [:formex_v4, :downloader, :start],
      [:formex_v4, :downloader, :complete],
      [:formex_v4, :downloader, :cache_hit],
      [:broadway, :processor, :message, :stop],
      [:broadway, :batch_processor, :start],
      [:broadway, :batch_processor, :stop]
    ]

    for event <- events do
      :telemetry.attach(
        {__MODULE__, event},
        event,
        &__MODULE__.handle_event/4,
        nil
      )
    end
  end

  defp detach_telemetry_handlers do
    events = [
      [:formex_v4, :producer, :start],
      [:formex_v4, :producer, :sparql_complete],
      [:formex_v4, :producer, :diff_complete],
      [:formex_v4, :downloader, :start],
      [:formex_v4, :downloader, :complete],
      [:formex_v4, :downloader, :cache_hit],
      [:broadway, :processor, :message, :stop],
      [:broadway, :batch_processor, :start],
      [:broadway, :batch_processor, :stop]
    ]

    for event <- events do
      :telemetry.detach({__MODULE__, event})
    end
  end

  @doc false
  def handle_event(event_name, measurements, metadata, _config) do
    if Process.whereis(__MODULE__) do
      send(__MODULE__, {:telemetry_event, event_name, measurements, metadata})
    end
  end

  defp handle_telemetry([:formex_v4, :producer, :start], _measurements, metadata, state) do
    total = Map.get(metadata, :total, 0)
    update_in(state, [:producer], fn p -> %{p | total: total, label: "Starting..."} end)
  end

  defp handle_telemetry([:formex_v4, :producer, :sparql_complete], _measurements, metadata, state) do
    count = Map.get(metadata, :count, 0)
    update_in(state, [:producer], fn p -> %{p | current: count, label: "SPARQL complete"} end)
  end

  defp handle_telemetry([:formex_v4, :producer, :diff_complete], _measurements, metadata, state) do
    new_count = Map.get(metadata, :new_count, 0)

    state
    |> update_in([:producer], fn p -> %{p | current: p.total, label: "Diff: #{new_count} new"} end)
    |> update_in([:downloads], fn d -> %{d | total: new_count} end)
    |> update_in([:processing], fn p -> %{p | total: new_count} end)
    |> update_in([:database], fn d -> %{d | total: new_count} end)
  end

  defp handle_telemetry([:formex_v4, :downloader, :start], _measurements, _metadata, state) do
    state
  end

  defp handle_telemetry([:formex_v4, :downloader, :complete], _measurements, metadata, state) do
    cached = Map.get(metadata, :cached, false)

    if cached do
      update_in(state, [:downloads], fn d ->
        %{d | current: d.current + 1, cached: d.cached + 1}
      end)
    else
      update_in(state, [:downloads], fn d -> %{d | current: d.current + 1} end)
    end
  end

  defp handle_telemetry([:formex_v4, :downloader, :cache_hit], _measurements, _metadata, state) do
    update_in(state, [:downloads], fn d -> %{d | cached: d.cached + 1} end)
  end

  defp handle_telemetry([:broadway, :processor, :message, :stop], _measurements, metadata, state) do
    status = metadata[:status]

    case status do
      :ok ->
        update_in(state, [:processing], fn p -> %{p | current: p.current + 1} end)

      {:failed, _reason} ->
        update_in(state, [:processing], fn p ->
          %{p | current: p.current + 1, failed: p.failed + 1}
        end)

      _ ->
        state
    end
  end

  defp handle_telemetry([:broadway, :batch_processor, :start], _measurements, metadata, state) do
    batch_size = length(metadata[:messages] || [])
    update_in(state, [:database], fn d -> %{d | current: d.current + batch_size} end)
  end

  defp handle_telemetry([:broadway, :batch_processor, :stop], _measurements, _metadata, state) do
    state
  end

  defp handle_telemetry(_event, _measurements, _metadata, state) do
    state
  end

  defp maybe_render(state) do
    now = System.monotonic_time(:millisecond)

    if now - state.last_render >= @update_interval_ms do
      MultiBarRenderer.render(state)
      %{state | last_render: now}
    else
      state
    end
  end
end
