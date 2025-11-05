defmodule XmlIngest.UriProducer do
  @moduledoc """
  GenStage producer for Broadway pipeline.
  Fetches URIs from SPARQL endpoint and computes diff with existing database.
  """

  use GenStage
  require Logger

  alias XmlIngest.{SparqlClient, XmlFileRepository}

  @doc """
  Starts the producer.
  
  ## Options
    - `:db_path` - Path to DuckDB database
    - `:sparql_opts` - Options passed to SparqlClient.fetch_uris/1
  """
  @impl true
  def init(opts) do
    db_path = Keyword.fetch!(opts, :db_path)
    sparql_opts = Keyword.get(opts, :sparql_opts, [])

    # Fetch URIs and compute diff on initialization
    state = fetch_and_diff(db_path, sparql_opts)
    
    {:producer, state}
  end

  @impl true
  def handle_demand(demand, state) do
    {uris_to_emit, remaining_uris} = Enum.split(state.pending_uris, demand)
    
    # Track progress
    new_processed = state.processed + length(uris_to_emit)
    
    if rem(new_processed, 100) == 0 and new_processed > 0 do
      percentage = Float.round(new_processed / state.total * 100, 1)
      Logger.info("📊 Progress: #{new_processed}/#{state.total} (#{percentage}%)")
    end

    # Convert URIs to Broadway messages
    messages = Enum.map(uris_to_emit, fn uri ->
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

    {:noreply, messages, new_state}
  end

  # Private: Fetch URIs from SPARQL and compute diff with database
  defp fetch_and_diff(db_path, sparql_opts) do
    Logger.info("🔄 Starting sync process...")
    
    # Fetch from SPARQL
    {:ok, sparql_uris} = SparqlClient.fetch_uris(sparql_opts)
    
    # Compute diff with database
    Logger.info("🗄️  Computing diff using database...")
    {:ok, new_uris} = XmlFileRepository.compute_new_uris(db_path, sparql_uris)
    
    Logger.info("📊 Diff: #{length(new_uris)} new files to process")
    
    %{
      pending_uris: new_uris,
      total: length(new_uris),
      processed: 0
    }
  end
end
