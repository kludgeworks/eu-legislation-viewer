defmodule FormexV4.Repository do
  @moduledoc """
  Repository for Formex document database operations.
  Encapsulates all database interactions with the formex_documents table.
  """

  require Logger

  @default_batch_size 500

  @doc """
  Filters a list of URIs to only include those not already in the database.
  Uses database-side filtering for efficiency with small chunks.

  ## Parameters
    - db_path: Path to DuckDB database file
    - uris: List of URIs to filter

  ## Returns
    - {:ok, list_of_new_uris} on success
    - {:error, reason} on failure

  ## Examples

      iex> Repository.filter_new_uris("formex.db", ["http://new.uri", "http://existing.uri"])
      {:ok, ["http://new.uri"]}
  """
  def filter_new_uris(db_path, uris) when is_list(uris) do
    if Enum.empty?(uris) do
      {:ok, []}
    else
      {:ok, db} = Duckdbex.open(db_path)
      {:ok, conn} = Duckdbex.connection(db)

      try do
        # Use temporary table approach to avoid parameter binding issues with array literals
        {:ok, _} = Duckdbex.query(conn, "CREATE TEMP TABLE candidate_uris (uri VARCHAR)")

        # Insert candidate URIs into temp table
        placeholders = Enum.map_join(1..length(uris), ", ", fn i -> "($#{i})" end)
        insert_sql = "INSERT INTO candidate_uris VALUES #{placeholders}"
        {:ok, _} = Duckdbex.query(conn, insert_sql, uris)

        # Query for URIs that don't exist in formex_documents
        sql = """
        SELECT uri FROM candidate_uris
        WHERE uri NOT IN (SELECT uri FROM formex_documents)
        """

        case Duckdbex.query(conn, sql) do
          {:ok, result} ->
            rows = Duckdbex.fetch_all(result)
            new_uris = Enum.map(rows, fn [uri] -> uri end)
            Duckdbex.release(result)
            {:ok, new_uris}

          {:error, reason} ->
            {:error, reason}
        end
      after
        # Clean up temp table and connection
        Duckdbex.query(conn, "DROP TABLE IF EXISTS candidate_uris")
        Duckdbex.release(conn)
        Duckdbex.release(db)
      end
    end
  end

  @doc """
  Returns the total count of documents in the formex_documents table.

  ## Parameters
    - db_path: Path to DuckDB database file

  ## Returns
    - {:ok, count} on success
    - {:error, reason} on failure

  ## Examples

      iex> Repository.count_files("formex.db")
      {:ok, 586432}
  """
  def count_files(db_path) do
    {:ok, db} = Duckdbex.open(db_path)
    {:ok, conn} = Duckdbex.connection(db)

    try do
      case Duckdbex.query(conn, "SELECT COUNT(*) FROM formex_documents") do
        {:ok, result} ->
          [[count]] = Duckdbex.fetch_all(result)
          Duckdbex.release(result)
          {:ok, count}

        {:error, reason} ->
          {:error, reason}
      end
    after
      Duckdbex.release(conn)
      Duckdbex.release(db)
    end
  end

  @doc """
  Inserts a batch of metadata records into the database.
  Accepts pre-extracted metadata maps from MetadataExtractor.

  ## Parameters
    - db_path: Path to DuckDB database file
    - metadata_list: List of metadata maps with all required fields
    - opts: Keyword list of options
      - batch_size: Number of records per batch (default: 500)

  ## Returns
    - {:ok, count} on success where count is number of files ingested
    - {:error, reason} on failure
  """
  def insert_metadata_batch(db_path, metadata_list, opts \\ []) when is_list(metadata_list) do
    batch_size = Keyword.get(opts, :batch_size, @default_batch_size)

    {:ok, db} = Duckdbex.open(db_path)
    {:ok, conn} = Duckdbex.connection(db)

    try do
      {:ok, _} = Duckdbex.query(conn, "BEGIN TRANSACTION")

      {count, _errors} =
        Enum.reduce(metadata_list, {0, 0}, fn metadata, {count, errors} ->
          insert_single_metadata(conn, metadata, count, errors, batch_size)
        end)

      # Commit remaining
      {:ok, _} = Duckdbex.query(conn, "COMMIT")

      {:ok, count}
    catch
      kind, reason ->
        Duckdbex.query(conn, "ROLLBACK")
        {:error, {kind, reason}}
    after
      Duckdbex.release(conn)
      Duckdbex.release(db)
    end
  end

  # Private helper: inserts a single metadata record
  defp insert_single_metadata(conn, metadata, count, errors, batch_size) do
    try do
      sql = build_insert_sql(metadata)

      # Check if ID already exists before insert to get accurate count
      exists_count = check_id_exists(conn, metadata.id)

      case Duckdbex.query(conn, sql) do
        {:ok, _result} ->
          handle_successful_insert(conn, exists_count, count, errors, batch_size)

        {:error, reason} ->
          Logger.warning("⚠️  Error inserting #{metadata.file_path}: #{inspect(reason)}")
          {count, errors + 1}
      end
    rescue
      e ->
        Logger.warning("⚠️  Error inserting #{metadata.file_path}: #{Exception.message(e)}")
        {count, errors + 1}
    end
  end

  # Private helper: builds INSERT SQL for metadata with proper SQL escaping
  # Uses string interpolation with escaping since DuckDB doesn't support
  # parameter binding with INSERT...SELECT...FROM table functions like read_text()
  defp build_insert_sql(metadata) do
    # Escape single quotes for SQL string literals
    escape = fn str -> if str, do: String.replace(str, "'", "''"), else: "NULL" end

    """
    INSERT INTO formex_documents
        (id, xml_content, version, root_element, file_size, uri, work_id, expr_id, man_id, cs_id)
    SELECT
        '#{escape.(metadata.id)}'::UUID,
        content,
        '#{escape.(metadata.version)}',
        '#{escape.(metadata.root_element)}',
        #{metadata.file_size},
        '#{escape.(metadata.uri)}',
        '#{escape.(metadata.work_id)}'::UUID,
        '#{escape.(metadata.expr_id)}',
        '#{escape.(metadata.man_id)}',
        '#{escape.(metadata.cs_id)}'
    FROM read_text('#{escape.(metadata.file_path)}')
    ON CONFLICT (id) DO NOTHING
    """
  end

  # Private helper: checks if ID exists in database using parameter binding
  defp check_id_exists(conn, id) do
    check_sql = "SELECT COUNT(*) FROM formex_documents WHERE id = $1"
    {:ok, check_result} = Duckdbex.query(conn, check_sql, [id])
    [[exists_count]] = Duckdbex.fetch_all(check_result)
    Duckdbex.release(check_result)
    exists_count
  end

  # Private helper: handles successful insert and batch commits
  defp handle_successful_insert(conn, exists_count, count, errors, batch_size) do
    # Only count if the ID didn't already exist (exists_count == 0)
    increment = if exists_count == 0, do: 1, else: 0
    new_count = count + increment

    # Commit batch periodically (only count actual inserts)
    if rem(count, batch_size) == batch_size - 1 and increment == 1 do
      {:ok, _} = Duckdbex.query(conn, "COMMIT")
      Logger.info("💾 Committed #{new_count} files")
      {:ok, _} = Duckdbex.query(conn, "BEGIN TRANSACTION")
    end

    {new_count, errors}
  end
end
