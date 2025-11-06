defmodule FormexV4.Ingest.UriProducer do
  @moduledoc """
  GenStage producer for Broadway pipeline.
  Fetches all URIs from SPARQL endpoint at startup and filters against database.

  ## Design Pattern: Finite Data Source with Broadway

  Broadway is designed for long-lived, continuous streaming pipelines where
  producers wait indefinitely for new data. However, this producer implements
  a "finite data source" pattern for batch job use cases:

  1. **Initialization**: All URIs are fetched once at startup (synchronous)
  2. **Dispatch**: URIs are emitted in response to demand from processors
  3. **Completion**: When all URIs are exhausted, emits a telemetry event
  4. **Persistence**: Producer stays alive (doesn't self-terminate)

  ### Why Not Self-Terminate?

  The producer does NOT stop itself when URIs are exhausted because:
  - Broadway's supervisor would restart it, causing an infinite loop
  - Broadway expects producers to be long-lived and wait for more data
  - Graceful shutdown should be coordinated by Broadway's Terminator process

  ### Completion Detection

  Since Broadway has no built-in "all messages processed" signal, we use
  telemetry events to notify consumers (e.g., Mix tasks, tests) that:
  1. Producer has dispatched all URIs (`:producer_complete` event)
  2. But batchers may still be processing messages for up to `batch_timeout` ms

  This pattern allows Mix tasks to wait for completion and then explicitly
  stop the pipeline with `Broadway.stop/3`.

  See: https://hexdocs.pm/broadway/Broadway.html#module-graceful-shutdown
  """

  use GenStage
  require Logger

  alias FormexV4.Repository
  alias FormexV4.Sparql.Client

  @doc """
  Starts the producer.

  ## Options
    - `:db_path` - Path to DuckDB database
    - `:sparql_opts` - Options passed to SPARQL client
      - `:endpoint` - SPARQL endpoint URL
      - `:limit` - Optional LIMIT for testing
      - `:timeout` - HTTP timeout
  """
  @impl true
  def init(opts) do
    db_path = Keyword.fetch!(opts, :db_path)
    sparql_opts = Keyword.get(opts, :sparql_opts, [])

    # Fetch and filter URIs synchronously at startup
    Logger.info("🔄 Starting sync process...")

    case fetch_and_filter_uris(db_path, sparql_opts) do
      {:ok, uris} ->
        Logger.info("📊 Ready to process #{length(uris)} new URIs")

        initial_state = %{
          pending_uris: uris,
          total: length(uris),
          processed: 0
        }

        {:producer, initial_state}

      {:error, reason} ->
        Logger.error("Failed to fetch URIs: #{inspect(reason)}")
        {:stop, reason}
    end
  end

  @impl true
  def handle_demand(demand, state) do
    {uris_to_emit, remaining_uris} = Enum.split(state.pending_uris, demand)

    # Track progress
    new_processed = state.processed + length(uris_to_emit)

    if rem(new_processed, 100) == 0 and new_processed > 0 and state.total > 0 do
      percentage = Float.round(new_processed / state.total * 100, 1)
      Logger.info("📊 Progress: #{new_processed}/#{state.total} (#{percentage}%)")
    end

    # Convert URIs to Broadway messages
    messages =
      Enum.map(uris_to_emit, fn uri ->
        %Broadway.Message{
          data: uri,
          acknowledger: {__MODULE__, :ack_id, :ack_data}
        }
      end)

    new_state = %{
      state
      | pending_uris: remaining_uris,
        processed: new_processed
    }

    # Signal completion when all URIs exhausted
    # NOTE: This emits a telemetry event but does NOT stop the producer.
    # Broadway expects producers to stay alive. Consumers (Mix tasks, tests)
    # should listen for this event and explicitly call Broadway.stop/3.
    # Remember: Batchers may still be processing for up to batch_timeout ms!
    if remaining_uris == [] and uris_to_emit != [] do
      Logger.info("✅ All URIs dispatched")

      :telemetry.execute(
        [:formex_v4, :producer, :complete],
        %{total: state.total},
        %{}
      )
    end

    {:noreply, messages, new_state}
  end

  @doc """
  Broadway acknowledger callback.
  Called when messages are successfully processed or failed.
  Since we're using a simple producer that doesn't need to track acks,
  this is a no-op implementation.
  """
  def ack(_ack_ref, successful, failed) do
    # Log failures for debugging
    if length(failed) > 0 do
      Logger.debug("Producer ack: #{length(successful)} successful, #{length(failed)} failed")
    end

    :ok
  end

  # Fetch all URIs from SPARQL and filter against database
  defp fetch_and_filter_uris(db_path, sparql_opts) do
    with {:ok, all_uris} <- Client.fetch_uris(sparql_opts),
         {:ok, new_uris} <- Repository.filter_new_uris(db_path, all_uris) do
      Logger.info(
        "   Filtered: #{length(all_uris)} total, #{length(new_uris)} new (#{length(all_uris) - length(new_uris)} already in database)"
      )

      {:ok, new_uris}
    end
  end
end
