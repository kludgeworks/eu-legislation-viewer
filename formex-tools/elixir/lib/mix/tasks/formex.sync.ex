defmodule Mix.Tasks.Formex.Sync do
  @moduledoc """
  Syncs Formex V4 files from Europa Publications SPARQL endpoint to DuckDB.

  ## Usage

      mix formex.sync [options]

  ## Options

    * `--db PATH` - Path to DuckDB database (default: formex.db)
    * `--endpoint URL` - SPARQL endpoint URL (default: https://publications.europa.eu/webapi/rdf/sparql)
    * `--limit N` - Limit SPARQL results (for testing)
    * `--concurrency N` - Number of concurrent downloads (default: 12)
    * `--batch-size N` - Database batch insert size (default: 500)
    * `--progress` - Enable progress bars (default: true if TTY)
    * `--no-progress` - Disable progress bars

  ## Examples

      # Sync with defaults
      mix formex.sync
      
      # Test with 100 files
      mix formex.sync --limit 100
      
      # Custom database and concurrency
      mix formex.sync --db mydb.db --concurrency 20
  """

  use Mix.Task
  require Logger

  alias FormexV4.Cache
  alias FormexV4.Ingest.Pipeline

  @shortdoc "Syncs Formex V4 files from Europa to DuckDB"

  @default_db_path "formex.db"
  @default_endpoint "https://publications.europa.eu/webapi/rdf/sparql"
  @default_concurrency 12
  @default_batch_size 500

  @impl Mix.Task
  def run(args) do
    # Parse arguments
    {opts, _remaining, _invalid} =
      OptionParser.parse(args,
        strict: [
          db: :string,
          endpoint: :string,
          limit: :integer,
          concurrency: :integer,
          batch_size: :integer,
          progress: :boolean
        ]
      )

    # Extract options with defaults
    db_path = Keyword.get(opts, :db, @default_db_path)
    endpoint = Keyword.get(opts, :endpoint, @default_endpoint)
    limit = Keyword.get(opts, :limit)
    concurrency = Keyword.get(opts, :concurrency, @default_concurrency)
    batch_size = Keyword.get(opts, :batch_size, @default_batch_size)
    progress = Keyword.get(opts, :progress, IO.ANSI.enabled?())

    # Start required applications
    {:ok, _} = Application.ensure_all_started(:req)
    {:ok, _} = Application.ensure_all_started(:duckdbex)
    {:ok, _} = Application.ensure_all_started(:saxy)
    {:ok, _} = Application.ensure_all_started(:jason)

    # Initialize file cache
    Cache.init()

    # Display configuration
    Logger.info("🚀 Starting Formex V4 sync")
    Logger.info("📁 Database: #{db_path}")
    Logger.info("🔗 Endpoint: #{endpoint}")
    if limit, do: Logger.info("🔢 Limit: #{limit}")
    Logger.info("⚡ Concurrency: #{concurrency}")
    Logger.info("📦 Batch size: #{batch_size}")

    # Start pipeline
    pipeline_opts = [
      db_path: db_path,
      endpoint: endpoint,
      limit: limit,
      download_concurrency: concurrency,
      batch_size: batch_size,
      progress: progress
    ]

    {:ok, _pid} = Pipeline.start_link(pipeline_opts)

    # Keep running until pipeline completes
    # Broadway will automatically stop when producer runs out of messages
    ref = Process.monitor(Pipeline)

    receive do
      {:DOWN, ^ref, :process, _pid, reason} ->
        # Stop telemetry reporter if running
        FormexV4.Telemetry.ProgressReporter.stop()

        case reason do
          :normal ->
            Logger.info("✅ Sync completed successfully")

          other ->
            Logger.error("❌ Sync failed: #{inspect(other)}")
            System.halt(1)
        end
    end
  end
end
