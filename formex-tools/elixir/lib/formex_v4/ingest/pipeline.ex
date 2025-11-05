defmodule FormexV4.Ingest.Pipeline do
  @moduledoc """
  Broadway pipeline for XML ingestion.

  Pipeline stages:
  1. Producer: Fetches URIs from SPARQL, computes diff with database
  2. Processor: Downloads XML files (with caching)
  3. Batcher: Batch inserts into DuckDB
  """

  use Broadway
  require Logger

  alias Broadway.Message
  alias FormexV4.{Cache, Repository}
  alias FormexV4.Ingest.Downloader
  alias FormexV4.Metadata.Extractor

  @default_db_path "formex.db"
  @default_concurrency 12
  @default_batch_size 500

  @doc """
  Starts the Broadway pipeline.

  ## Options
    - `:db_path` - Path to DuckDB database (default: "formex.db")
    - `:sparql_endpoint` - SPARQL endpoint URL
    - `:sparql_limit` - Optional SPARQL LIMIT for testing
    - `:download_concurrency` - Number of concurrent downloads (default: 12)
    - `:batch_size` - Database batch insert size (default: 500)
    - `:progress` - Enable progress bars (default: true if TTY)

  ## Pipeline Stages
    1. Producer: Fetches URIs from SPARQL, computes diff with database
    2. Processor: Downloads XML files and extracts metadata (parallel)
    3. Batcher: Batch inserts into DuckDB (single-threaded)
  """
  def start_link(opts) do
    db_path = Keyword.get(opts, :db_path, @default_db_path)
    sparql_opts = Keyword.take(opts, [:endpoint, :limit])
    download_concurrency = Keyword.get(opts, :download_concurrency, @default_concurrency)
    batch_size = Keyword.get(opts, :batch_size, @default_batch_size)
    progress = Keyword.get(opts, :progress, IO.ANSI.enabled?())

    # Start telemetry reporter if progress enabled
    if progress do
      FormexV4.Telemetry.ProgressReporter.start_link(enabled: true)
    end

    Broadway.start_link(__MODULE__,
      name: __MODULE__,
      context: %{
        db_path: db_path,
        stats: %{
          downloaded: 0,
          cached: 0,
          failed: 0,
          ingested: 0,
          extraction_failed: 0
        }
      },
      producer: [
        module: {
          FormexV4.Ingest.UriProducer,
          db_path: db_path, sparql_opts: sparql_opts
        },
        concurrency: 1
      ],
      processors: [
        default: [
          concurrency: download_concurrency,
          max_demand: 10
        ]
      ],
      batchers: [
        duckdb: [
          concurrency: 1,
          batch_size: batch_size,
          batch_timeout: 5000
        ]
      ]
    )
  end

  @impl true
  def handle_message(:default, %Message{data: uri} = message, _context) do
    # Check if cached before downloading
    was_cached? = Cache.cached?(uri)

    with {:ok, file_path} <- download_file(uri),
         {:ok, metadata} <- Extractor.extract(file_path, uri) do
      # Success: forward metadata to batcher
      message
      |> Message.update_data(fn _ -> metadata end)
      |> Message.put_batcher(:duckdb)
      |> then(fn msg ->
        if was_cached? do
          Message.put_batch_key(msg, :cached)
        else
          Message.put_batch_key(msg, :downloaded)
        end
      end)
    else
      {:error, reason} ->
        Logger.warning("⚠️  Failed to process #{uri}: #{inspect(reason)}")
        Message.failed(message, reason)
    end
  end

  @impl true
  def handle_batch(:duckdb, messages, _batch_info, context) do
    # Extract metadata maps from messages
    metadata_list = Enum.map(messages, & &1.data)

    case insert_batch(metadata_list, context.db_path) do
      {:ok, inserted_count} ->
        Logger.info("💾 Committed #{inserted_count} files")

        # Clean up cache files after successful insert
        Enum.each(metadata_list, fn metadata ->
          Cache.delete(metadata.uri)
        end)

        messages

      {:error, reason} ->
        Logger.error("❌ Batch insert failed: #{inspect(reason)}")
        Enum.map(messages, &Message.failed(&1, reason))
    end
  end

  @impl true
  def handle_failed(messages, _context) do
    Enum.each(messages, fn message ->
      Logger.error("Failed message: #{inspect(message.data)}")
    end)

    messages
  end

  # Private: Download file and return cache file path
  defp download_file(uri) do
    case Downloader.download(uri) do
      {:ok, _content} ->
        file_path = Cache.cache_path(uri)
        {:ok, file_path}

      {:error, reason} ->
        {:error, reason}
    end
  end

  # Private: Batch insert metadata into DuckDB
  defp insert_batch(metadata_list, db_path) do
    Repository.insert_metadata_batch(db_path, metadata_list)
  end
end
