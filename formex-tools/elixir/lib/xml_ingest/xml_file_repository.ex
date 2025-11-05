defmodule XmlIngest.XmlFileRepository do
  @moduledoc """
  Repository for XML file database operations.
  Encapsulates all database interactions with the xml_files table.
  """

  require Logger

  @default_batch_size 500

  @doc """
  Computes new URIs by comparing SPARQL results against existing database URIs.
  Uses a temporary table for efficient database-level comparison.

  ## Parameters
    - db_path: Path to DuckDB database file
    - sparql_uris: List of URIs from SPARQL query

  ## Returns
    - {:ok, list_of_new_uris} on success
    - {:error, reason} on failure
  """
  def compute_new_uris(db_path, sparql_uris) when is_list(sparql_uris) do
    {:ok, db} = Duckdbex.open(db_path)
    {:ok, conn} = Duckdbex.connection(db)

    try do
      # Create temporary table
      {:ok, _} = Duckdbex.query(conn, "CREATE TEMP TABLE sparql_results (uri VARCHAR)")

      # Insert SPARQL URIs into temp table in batches
      batch_size = 1000

      Enum.chunk_every(sparql_uris, batch_size)
      |> Enum.each(fn batch ->
        placeholders = Enum.map_join(1..length(batch), ", ", fn i -> "($#{i})" end)
        sql = "INSERT INTO sparql_results VALUES #{placeholders}"

        {:ok, _} = Duckdbex.query(conn, sql, batch)
      end)

      # Query for URIs that don't exist in xml_files
      sql = """
      SELECT uri FROM sparql_results
      WHERE uri NOT IN (SELECT uri FROM xml_files)
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
      Duckdbex.query(conn, "DROP TABLE IF EXISTS sparql_results")
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

  # Private helper: builds INSERT SQL for metadata
  defp build_insert_sql(metadata) do
    """
    INSERT INTO xml_files
        (id, xml_content, version, root_element, file_size, uri, work_id, expr_id, man_id, cs_id)
    SELECT
        #{format_uuid(metadata.id)},
        content,
        #{escape_sql(metadata.version)},
        #{escape_sql(metadata.root_element)},
        #{metadata.file_size},
        #{escape_sql(metadata.uri)},
        #{format_uuid(metadata.work_id)},
        #{escape_sql(metadata.expr_id)},
        #{escape_sql(metadata.man_id)},
        #{escape_sql(metadata.cs_id)}
    FROM read_text(#{escape_sql(metadata.file_path)})
    ON CONFLICT (id) DO NOTHING
    """
  end

  # Private helper: checks if ID exists in database
  defp check_id_exists(conn, id) do
    check_sql = "SELECT COUNT(*) FROM xml_files WHERE id = #{format_uuid(id)}"
    {:ok, check_result} = Duckdbex.query(conn, check_sql)
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

  # Private helper: escapes single quotes in SQL string literals
  defp escape_sql(nil), do: "NULL"
  defp escape_sql(val), do: "'#{String.replace(to_string(val), "'", "''")}'"

  # Private helper: formats UUID values - checks if it's a valid UUID format
  defp format_uuid(nil), do: "NULL"

  defp format_uuid(val) do
    val_str = to_string(val)
    # Check if it looks like a UUID (8-4-4-4-12 format)
    if Regex.match?(~r/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i, val_str) do
      "#{escape_sql(val_str)}::UUID"
    else
      escape_sql(val_str)
    end
  end
end
