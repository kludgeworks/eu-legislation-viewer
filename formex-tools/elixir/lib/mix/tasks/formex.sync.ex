defmodule Mix.Tasks.Formex.Sync do
  @moduledoc """
  Syncs Formex V4 files from Europa Publications SPARQL endpoint to DuckDB.

  ## Usage

      mix formex.sync [options]

  ## Options

    * `--db PATH` - Path to DuckDB database (default: formex.db)
    * `--endpoint URL` - SPARQL endpoint URL (default: https://publications.europa.eu/webapi/rdf/sparql)
    * `--limit N` - Limit SPARQL results (for testing)
    * `--chunk-size N` - SPARQL pagination chunk size (default: 5000)
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
  @default_chunk_size 5000
  @default_concurrency 12
  @default_batch_size 500
  @max_wait_time 3_600_000  # 1 hour timeout for very large syncs

  @impl Mix.Task
  def run(args) do
    # Parse arguments
    {opts, _remaining, _invalid} =
      OptionParser.parse(args,
        strict: [
          db: :string,
          endpoint: :string,
          limit: :integer,
          chunk_size: :integer,
          concurrency: :integer,
          batch_size: :integer,
          progress: :boolean
        ]
      )

    # Extract options with defaults
    db_path = Keyword.get(opts, :db, @default_db_path)
    endpoint = Keyword.get(opts, :endpoint, @default_endpoint)
    limit = Keyword.get(opts, :limit)
    chunk_size = Keyword.get(opts, :chunk_size, @default_chunk_size)
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
    Logger.info("📊 Chunk size: #{chunk_size}")
    Logger.info("⚡ Concurrency: #{concurrency}")
    Logger.info("📦 Batch size: #{batch_size}")

    # Attach telemetry handler BEFORE starting pipeline to avoid race condition
    # Broadway is designed for long-lived pipelines, so we need to explicitly
    # detect completion and stop the pipeline for batch job use cases.
    task_pid = self()

    # Suppress "local function" performance warning for anonymous telemetry handler
    Logger.configure(level: :warning)
    :telemetry.attach(
      "mix-task-producer-complete",
      [:formex_v4, :producer, :complete],
      fn _name, measurements, _metadata, _config ->
        total = Map.get(measurements, :total, 0)
        Logger.info("📨 Producer finished dispatching #{total} URIs, waiting for batches to flush...")
        send(task_pid, :producer_complete)
      end,
      nil
    )
    Logger.configure(level: :info)

    # Start pipeline AFTER attaching telemetry handler
    pipeline_opts = [
      db_path: db_path,
      endpoint: endpoint,
      limit: limit,
      chunk_size: chunk_size,
      download_concurrency: concurrency,
      batch_size: batch_size,
      progress: progress
    ]

    {:ok, pipeline_pid} = Pipeline.start_link(pipeline_opts)

    # Monitor the pipeline process
    ref = Process.monitor(pipeline_pid)

    # Wait for producer to finish dispatching all URIs
    receive do
      :producer_complete ->
        # Give Broadway time to process remaining messages and flush batches
        # This accounts for batch_timeout plus buffer for processing
        flush_wait_time = Pipeline.batch_flush_wait_time()
        Logger.debug("Waiting #{flush_wait_time}ms for batch flush...")
        Process.sleep(flush_wait_time)

        # Now stop the pipeline gracefully
        Logger.info("🛑 Stopping pipeline...")
        Broadway.stop(pipeline_pid, :normal, 10_000)

        # Cleanup
        :telemetry.detach("mix-task-producer-complete")
        FormexV4.Telemetry.ProgressReporter.stop()

        Logger.info("✅ Sync completed successfully")

      {:DOWN, ^ref, :process, _pid, reason} ->
        # Pipeline crashed before completion
        :telemetry.detach("mix-task-producer-complete")
        FormexV4.Telemetry.ProgressReporter.stop()

        case reason do
          :normal ->
            Logger.info("✅ Sync completed successfully")

          other ->
            Logger.error("❌ Sync failed: #{inspect(other)}")
            System.halt(1)
        end
    after
      @max_wait_time ->
        Logger.error("❌ Sync timed out after #{@max_wait_time}ms")
        :telemetry.detach("mix-task-producer-complete")
        FormexV4.Telemetry.ProgressReporter.stop()
        Broadway.stop(pipeline_pid, :shutdown, 5_000)
        System.halt(1)
    end
  end
end
