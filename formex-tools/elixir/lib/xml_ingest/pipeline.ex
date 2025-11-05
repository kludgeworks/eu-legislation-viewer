defmodule XmlIngest.Pipeline do
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
  alias XmlIngest.{XmlFileRepository, Downloader, FileCache}

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
  """
  def start_link(opts) do
    db_path = Keyword.get(opts, :db_path, @default_db_path)
    sparql_opts = Keyword.take(opts, [:endpoint, :limit])
    download_concurrency = Keyword.get(opts, :download_concurrency, @default_concurrency)
    batch_size = Keyword.get(opts, :batch_size, @default_batch_size)

    Broadway.start_link(__MODULE__,
      name: __MODULE__,
      context: %{
        db_path: db_path,
        stats: %{
          downloaded: 0,
          cached: 0,
          failed: 0,
          ingested: 0
        }
      },
      producer: [
        module: {
          XmlIngest.UriProducer,
          db_path: db_path,
          sparql_opts: sparql_opts
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
    was_cached? = FileCache.cached?(uri)
    
    case process_uri(uri) do
      {:ok, {file_path, uri}} ->
        message
        |> Message.update_data(fn _ -> {file_path, uri} end)
        |> Message.put_batcher(:duckdb)
        |> then(fn msg ->
          if was_cached? do
            Message.put_batch_key(msg, :cached)
          else
            Message.put_batch_key(msg, :downloaded)
          end
        end)

      {:error, reason} ->
        Logger.warning("⚠️  Failed to process #{uri}: #{inspect(reason)}")
        Message.failed(message, reason)
    end
  end

  @impl true
  def handle_batch(:duckdb, messages, _batch_info, context) do
    # Extract {file_path, uri} pairs
    path_uri_pairs = Enum.map(messages, & &1.data)
    
    case insert_batch(path_uri_pairs, context.db_path) do
      {:ok, inserted_count} ->
        Logger.info("💾 Committed #{inserted_count} files")
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

  # Private: Process a single URI (download and return cache file path)
  defp process_uri(uri) do
    # Download (uses cache if available)
    case Downloader.download(uri) do
      {:ok, _content} ->
        # Get the cached file path
        file_path = FileCache.cache_path(uri)
        {:ok, {file_path, uri}}

      {:error, reason} ->
        {:error, reason}
    end
  end

  # Private: Batch insert into DuckDB
  defp insert_batch(path_uri_pairs, db_path) do
    # Convert list of {path, uri} tuples to map
    path_uri_map = Map.new(path_uri_pairs)
    
    # Insert into database (repository handles UUID generation and metadata extraction)
    XmlFileRepository.insert_files_with_uris(db_path, path_uri_map)
  end
end
