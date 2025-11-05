defmodule XmlIngest.XmlFileRepository do
  @moduledoc """
  Repository for XML file database operations.
  Encapsulates all database interactions with the xml_files table.
  """

  import Bitwise
  require Logger

  @default_batch_size 500

  # Pattern to extract cellar identifier components from URI
  @cellar_pattern ~r/([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})\.(\d{4})\.(\d{2})\/([A-Z0-9_]+)/i

  # Namespace UUID for deterministic UUID v5 generation
  # SHA-1 hash of "http://publications.europa.eu/resource/cellar" using URL namespace
  @namespace_uuid "9073926b-929f-5cc3-8115-ca15e6b37a6f"

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
  Inserts files with explicit URIs into the database.
  Useful when URIs come from external sources (e.g., SPARQL queries).

  ## Parameters
    - db_path: Path to DuckDB database file
    - path_uri_map: Map of file paths to their URIs
    - opts: Keyword list of options
      - batch_size: Number of records per batch (default: 500)

  ## Returns
    - {:ok, count} on success where count is number of files ingested
    - {:error, reason} on failure
  """
  def insert_files_with_uris(db_path, path_uri_map, opts \\ []) when is_map(path_uri_map) do
    batch_size = Keyword.get(opts, :batch_size, @default_batch_size)
    entries = Enum.map(path_uri_map, fn {path, uri} -> {path, uri} end)
    insert_files_internal(db_path, entries, batch_size)
  end

  @doc """
  Checks if a URI already exists in the database.

  ## Parameters
    - db_path: Path to DuckDB database file
    - uri: URI to check

  ## Returns
    - {:ok, true} if URI exists
    - {:ok, false} if URI does not exist
    - {:error, reason} on failure
  """
  def uri_exists?(db_path, uri) do
    {:ok, db} = Duckdbex.open(db_path)
    {:ok, conn} = Duckdbex.connection(db)

    try do
      sql = "SELECT COUNT(*) FROM xml_files WHERE uri = $1"

      case Duckdbex.query(conn, sql, [uri]) do
        {:ok, result} ->
          [[count]] = Duckdbex.fetch_all(result)
          Duckdbex.release(result)
          {:ok, count > 0}

        {:error, reason} ->
          {:error, reason}
      end
    after
      Duckdbex.release(conn)
      Duckdbex.release(db)
    end
  end

  @doc """
  Gets all URIs from the database.

  ## Parameters
    - db_path: Path to DuckDB database file

  ## Returns
    - {:ok, list_of_uris} on success
    - {:error, reason} on failure
  """
  def get_all_uris(db_path) do
    {:ok, db} = Duckdbex.open(db_path)
    {:ok, conn} = Duckdbex.connection(db)

    try do
      case Duckdbex.query(conn, "SELECT uri FROM xml_files") do
        {:ok, result} ->
          rows = Duckdbex.fetch_all(result)
          uris = Enum.map(rows, fn [uri] -> uri end)
          Duckdbex.release(result)
          {:ok, uris}

        {:error, reason} ->
          {:error, reason}
      end
    after
      Duckdbex.release(conn)
      Duckdbex.release(db)
    end
  end

  # ==================== Private Helper Methods ====================

  defp insert_files_internal(db_path, entries, batch_size) when is_list(entries) do
    {:ok, db} = Duckdbex.open(db_path)
    {:ok, conn} = Duckdbex.connection(db)

    sql = """
    INSERT INTO xml_files
        (id, xml_content, version, root_element, file_size, uri, work_id, expr_id, man_id, cs_id)
    VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
    ON CONFLICT (id) DO NOTHING
    """

    try do
      {:ok, _} = Duckdbex.query(conn, "BEGIN TRANSACTION")

      {count, _errors} =
        Enum.reduce(entries, {0, 0}, fn {path, uri}, {count, errors} ->
          try do
            # Get file size
            %{size: size} = File.stat!(path)

            # Read file content
            xml_content = File.read!(path)

            # Parse XML metadata
            %{root_element: root, version: version} = parse_xml_metadata(path)

            # Extract cellar identifiers from URI
            %{work_id: work_id, expr_id: expr_id, man_id: man_id, cs_id: cs_id} =
              extract_cellar_ids(uri)

            # Generate deterministic UUID based on content
            content_id = generate_file_id(path)

            # Execute insert
            params = [
              content_id,
              xml_content,
              version || "",
              root,
              size,
              uri,
              work_id,
              expr_id,
              man_id,
              cs_id
            ]

            case Duckdbex.query(conn, sql, params) do
              {:ok, result} ->
                # Get the number of rows inserted (0 if ON CONFLICT)
                [[rows_inserted]] = Duckdbex.fetch_all(result)
                Duckdbex.release(result)
                
                new_count = count + rows_inserted

                # Commit batch periodically
                if rem(new_count, batch_size) == 0 do
                  {:ok, _} = Duckdbex.query(conn, "COMMIT")
                  Logger.info("💾 Committed #{new_count} files")
                  {:ok, _} = Duckdbex.query(conn, "BEGIN TRANSACTION")
                end

                {new_count, errors}

              {:error, reason} ->
                Logger.warning("⚠️  Error ingesting #{path}: #{inspect(reason)}")
                {count, errors + 1}
            end
          rescue
            e ->
              Logger.warning("⚠️  Error ingesting #{path}: #{Exception.message(e)}")
              {count, errors + 1}
          end
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

  defp parse_xml_metadata(path) do
    File.stream!(path, [], 8192)
    |> Enum.reduce_while(%{root_element: "unknown", version: nil}, fn chunk, acc ->
      case Saxy.parse_string(chunk, XmlIngest.XmlMetadataHandler, acc) do
        {:ok, result} ->
          {:halt, result}

        {:halt, result, _remaining} ->
          {:halt, result}

        {:error, _} ->
          {:cont, acc}
      end
    end)
  end

  defp extract_cellar_ids(uri_or_path) do
    case Regex.run(@cellar_pattern, uri_or_path) do
      [_, work_id, expr_id, man_id, cs_id] ->
        %{
          work_id: work_id,
          expr_id: expr_id,
          man_id: man_id,
          cs_id: cs_id
        }

      nil ->
        %{work_id: nil, expr_id: nil, man_id: nil, cs_id: nil}
    end
  end

  defp generate_file_id(path) do
    content_hash = hash_content(path)
    # Generate UUID v5 using namespace and content hash
    generate_uuid_v5(@namespace_uuid, content_hash)
  end

  defp hash_content(path) do
    File.stream!(path, [], 2048)
    |> Enum.reduce(:crypto.hash_init(:sha256), fn chunk, acc ->
      :crypto.hash_update(acc, chunk)
    end)
    |> :crypto.hash_final()
    |> Base.encode16(case: :lower)
  end

  defp generate_uuid_v5(namespace_uuid, name) do
    # Parse namespace UUID
    namespace_binary = parse_uuid(namespace_uuid)

    # Create UUID v5: SHA-1 hash of namespace + name
    hash =
      :crypto.hash(:sha, namespace_binary <> name)
      |> :binary.bin_to_list()

    # Format as UUID v5 (version 5, variant 1)
    <<a::32, b::16, c::16, d::16, e::48>> =
      <<
        Enum.at(hash, 0)::8,
        Enum.at(hash, 1)::8,
        Enum.at(hash, 2)::8,
        Enum.at(hash, 3)::8,
        Enum.at(hash, 4)::8,
        Enum.at(hash, 5)::8,
        # Set version to 5
        bor(band(Enum.at(hash, 6), 0x0F), 0x50)::8,
        Enum.at(hash, 7)::8,
        # Set variant to 1
        bor(band(Enum.at(hash, 8), 0x3F), 0x80)::8,
        Enum.at(hash, 9)::8,
        Enum.at(hash, 10)::8,
        Enum.at(hash, 11)::8,
        Enum.at(hash, 12)::8,
        Enum.at(hash, 13)::8,
        Enum.at(hash, 14)::8,
        Enum.at(hash, 15)::8
      >>

    # Format as UUID string
    "#{Integer.to_string(a, 16) |> String.downcase() |> String.pad_leading(8, "0")}-#{Integer.to_string(b, 16) |> String.downcase() |> String.pad_leading(4, "0")}-#{Integer.to_string(c, 16) |> String.downcase() |> String.pad_leading(4, "0")}-#{Integer.to_string(d, 16) |> String.downcase() |> String.pad_leading(4, "0")}-#{Integer.to_string(e, 16) |> String.downcase() |> String.pad_leading(12, "0")}"
  end

  defp parse_uuid(uuid_string) do
    uuid_string
    |> String.replace("-", "")
    |> Base.decode16!(case: :mixed)
  end
end
