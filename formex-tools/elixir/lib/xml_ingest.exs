defmodule XmlIngest do
  @moduledoc """
  Ingest XML documents from URIs into DuckDB with supervision and progress tracking.

  Run with: elixir xml_ingest.exs
  Or compile: mix run
  """

  # ============================================================================
  # Application - Top Level Supervisor
  # ============================================================================

  defmodule Application do
    use Application
    require Logger

    def start(_type, _args) do
      Logger.info("Starting XML Ingest application...")

      children = [
        # Broadway pipeline with built-in supervision
        XmlIngest.Pipeline
      ]

      opts = [
        strategy: :one_for_one,
        name: XmlIngest.Supervisor,
        max_restarts: 3,
        max_seconds: 5
      ]

      Supervisor.start_link(children, opts)
    end
  end

  # ============================================================================
  # Broadway Pipeline - Automatically Supervised
  # ============================================================================

  defmodule Pipeline do
    use Broadway
    require Logger

    alias Broadway.Message

    def start_link(_opts) do
      Broadway.start_link(__MODULE__,
        name: __MODULE__,
        producer: [
          module: {XmlIngest.UriProducer, []},
          concurrency: 1
        ],
        processors: [
          default: [
            concurrency: 50,    # 50 parallel downloads
            max_demand: 10
          ]
        ],
        batchers: [
          duckdb: [
            concurrency: 4,     # 4 concurrent batch inserters
            batch_size: 1000,   # Insert 1000 at a time
            batch_timeout: 5000 # Or every 5 seconds
          ]
        ]
      )
    end

    @impl true
    def handle_message(:default, %Message{data: uri} = message, _context) do
      """
      Process each URI: download and extract metadata.
      If this crashes, supervisor restarts just this processor.
      """
      case process_uri(uri) do
        {:ok, record} ->
          message
          |> Message.update_data(fn _ -> record end)
          |> Message.put_batcher(:duckdb)

        {:error, reason} ->
          Logger.warning("Failed #{uri}: #{inspect(reason)}")
          Message.failed(message, reason)
      end
    end

    @impl true
    def handle_batch(:duckdb, messages, _batch_info, _context) do
      """
      Insert batch into DuckDB.
      If this crashes, supervisor restarts just this batcher.
      """
      records = Enum.map(messages, & &1.data)

      case insert_batch(records) do
        :ok ->
          messages

        {:error, reason} ->
          Logger.error("Batch insert failed: #{inspect(reason)}")
          Enum.map(messages, &Message.failed(&1, reason))
      end
    end

    # Process a single URI
    defp process_uri(uri) do
      download_dir = "/data/xml"

      with {:ok, path} <- download_to_disk(uri, download_dir),
           celex <- extract_root_attribute(path, "celex") do
        {:ok, %{uri: uri, path: path, celex: celex}}
      end
    end

    # Download URI to disk, streaming (no memory load)
    defp download_to_disk(uri, target_dir) do
      filename = :crypto.hash(:md5, uri)
                 |> Base.encode16(case: :lower)
                 |> Kernel.<>(".xml")

      target_path = Path.join(target_dir, filename)
      File.mkdir_p!(target_dir)

      case Req.get(uri, into: File.stream!(target_path)) do
        {:ok, _} -> {:ok, target_path}
        {:error, reason} -> {:error, reason}
      end
    end

    # Extract attribute from root element using streaming XML parser
    defp extract_root_attribute(file_path, attr_name) do
      """
      Uses Saxy streaming - only reads until root element.
      Handles 100MB+ files without loading into memory.
      """
      handler = %{
        attr_name: attr_name,
        result: nil
      }

      # Stream parse file
      case File.stream!(file_path, [], 8192)
           |> Saxy.parse_stream(RootAttributeHandler, handler) do
        {:halted, result} -> result
        {:ok, _} -> nil
        {:error, _} -> nil
      end
    end

    # Batch insert into DuckDB
    defp insert_batch(records) do
      conn = get_db_conn()

      placeholders = Enum.map_join(records, ", ", fn _ ->
        "(?, ?, ?, read_text(?))"
      end)

      sql = """
      INSERT INTO documents (uri, path, celex, content)
      VALUES #{placeholders}
      """

      values = Enum.flat_map(records, fn r ->
        [r.uri, r.path, r.celex, r.path]
      end)

      {:ok, statement} = Exqlite.Sqlite3.prepare(conn, sql)
      :ok = Exqlite.Sqlite3.bind(conn, statement, values)

      case Exqlite.Sqlite3.step(conn, statement) do
        :done -> :ok
        {:error, reason} -> {:error, reason}
      end
    rescue
      e -> {:error, Exception.message(e)}
    end

    defp get_db_conn do
      {:ok, conn} = Exqlite.Sqlite3.open("mydb.duckdb")
      conn
    end
  end

  # ============================================================================
  # Saxy Handler for Streaming XML Parsing
  # ============================================================================

  defmodule RootAttributeHandler do
    @behaviour Saxy.Handler

    @impl true
    def handle_event(:start_document, _prolog, state) do
      {:ok, state}
    end

    @impl true
    def handle_event(:start_element, {_name, attributes}, state) do
      # Found root element - extract attribute and stop parsing
      attr_value = Enum.find_value(attributes, fn {key, value} ->
        if key == state.attr_name, do: value
      end)

      {:halt, attr_value}
    end

    @impl true
    def handle_event(_, _, state), do: {:ok, state}
  end

  # ============================================================================
  # Custom URI Producer - Feeds URIs into Pipeline
  # ============================================================================

  defmodule UriProducer do
    use GenStage
    require Logger

    def start_link(opts) do
      GenStage.start_link(__MODULE__, opts)
    end

    @impl true
    def init(_opts) do
      # Fetch all URIs from API
      all_uris = fetch_all_uris()
      Logger.info("Fetched #{length(all_uris)} total URIs")

      # Query database for what's already processed
      existing = query_existing_uris()
      Logger.info("Found #{MapSet.size(existing)} existing URIs in database")

      # Only process missing URIs (resumable!)
      missing = Enum.reject(all_uris, &MapSet.member?(existing, &1))
      Logger.info("Processing #{length(missing)} missing URIs")

      {:producer, %{uris: missing, demand: 0, total: length(missing), processed: 0}}
    end

    @impl true
    def handle_demand(incoming_demand, state) do
      %{uris: uris, demand: pending_demand, total: total, processed: processed} = state

      total_demand = incoming_demand + pending_demand
      {emit_uris, remaining_uris} = Enum.split(uris, total_demand)

      # Show progress
      new_processed = processed + length(emit_uris)
      if rem(new_processed, 100) == 0 and new_processed > 0 do
        percentage = Float.round(new_processed / total * 100, 1)
        Logger.info("Progress: #{new_processed}/#{total} (#{percentage}%)")
      end

      # Wrap in Broadway messages
      messages = Enum.map(emit_uris, fn uri ->
        %Broadway.Message{
          data: uri,
          acknowledger: {__MODULE__, :ack_id, :ack_data}
        }
      end)

      new_demand = max(total_demand - length(emit_uris), 0)
      new_state = %{
        uris: remaining_uris,
        demand: new_demand,
        total: total,
        processed: new_processed
      }

      {:noreply, messages, new_state}
    end

    defp fetch_all_uris do
      # Fetch from your API endpoint
      response = Req.get!("https://api.example.com/documents")
      response.body
    end

    defp query_existing_uris do
      {:ok, conn} = Exqlite.Sqlite3.open("mydb.duckdb")

      sql = "SELECT uri FROM documents"
      {:ok, statement} = Exqlite.Sqlite3.prepare(conn, sql)

      case Exqlite.Sqlite3.fetch_all(conn, statement) do
        {:ok, rows} ->
          rows
          |> Enum.map(fn [uri] -> uri end)
          |> MapSet.new()
        {:error, _} ->
          # Table might not exist yet
          MapSet.new()
      end
    end
  end

  # ============================================================================
  # Database Setup
  # ============================================================================

  defmodule Database do
    require Logger

    def init_schema do
      {:ok, conn} = Exqlite.Sqlite3.open("mydb.duckdb")

      sql = """
      CREATE TABLE IF NOT EXISTS documents (
        uri VARCHAR PRIMARY KEY,
        path VARCHAR NOT NULL,
        celex VARCHAR,
        content VARCHAR,
        ingested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      )
      """

      case Exqlite.Sqlite3.execute(conn, sql) do
        :ok ->
          Logger.info("Database schema initialized")
          :ok
        {:error, reason} ->
          Logger.error("Failed to initialize schema: #{inspect(reason)}")
          {:error, reason}
      end
    end
  end

  # ============================================================================
  # Main Entry Point
  # ============================================================================

  def run do
    # Start required applications
    {:ok, _} = Application.ensure_all_started(:req)
    {:ok, _} = Application.ensure_all_started(:exqlite)
    {:ok, _} = Application.ensure_all_started(:saxy)

    # Initialize database
    :ok = Database.init_schema()

    # Start the application (which starts the supervised pipeline)
    {:ok, _} = Application.start(XmlIngest.Application, [])

    # Keep running
    Process.sleep(:infinity)
  end
end
