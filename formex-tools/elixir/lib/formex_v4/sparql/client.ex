defmodule FormexV4.Sparql.Client do
  @moduledoc """
  Client for querying the Europa Publications SPARQL endpoint.
  Fetches content stream URIs for XML documents with type=fmx4.
  Uses chunked fetching with LIMIT/OFFSET for memory efficiency.
  """

  require Logger

  @default_endpoint "https://publications.europa.eu/webapi/rdf/sparql"
  @default_chunk_size 5_000
  @default_timeout 120_000

  @sparql_query_base """
  PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
  PREFIX cmr: <http://publications.europa.eu/ontology/cdm/cmr#>
  PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

  SELECT DISTINCT ?item
  WHERE {
    ?expression cmr:lang "eng"^^xsd:language .
    ?manifestation cdm:manifestation_manifests_expression ?expression .
    ?item cdm:item_belongs_to_manifestation ?manifestation .
    ?item cmr:manifestationMimeType "application/xml;type=fmx4" .
    <<FILTER>>
  }
  ORDER BY ?item
  """

  @sparql_count_query """
  PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
  PREFIX cmr: <http://publications.europa.eu/ontology/cdm/cmr#>
  PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

  SELECT (COUNT(DISTINCT ?item) AS ?count)
  WHERE {
    ?expression cmr:lang "eng"^^xsd:language .
    ?manifestation cdm:manifestation_manifests_expression ?expression .
    ?item cdm:item_belongs_to_manifestation ?manifestation .
    ?item cmr:manifestationMimeType "application/xml;type=fmx4" .
  }
  """

  @doc """
  Counts total number of URIs available from the SPARQL endpoint.

  ## Options
    - `:endpoint` - SPARQL endpoint URL (default: Europa Publications endpoint)
    - `:timeout` - HTTP timeout in milliseconds (default: 120_000)

  ## Returns
    - `{:ok, count}` on success
    - `{:error, reason}` on failure
  """
  def count_uris(opts \\ []) do
    endpoint = Keyword.get(opts, :endpoint, @default_endpoint)
    timeout = Keyword.get(opts, :timeout, @default_timeout)

    Logger.info("🔍 Counting URIs from SPARQL endpoint...")

    :telemetry.execute([:formex_v4, :sparql, :count_start], %{}, %{})

    request_opts = [
      method: :post,
      url: endpoint,
      headers: [
        {"Accept", "application/sparql-results+json"},
        {"Content-Type", "application/sparql-query"}
      ],
      body: @sparql_count_query,
      receive_timeout: timeout,
      retry: :transient,
      max_retries: 3
    ]

    case Req.request(request_opts) do
      {:ok, %{status: 200, body: body}} ->
        count = parse_count_response(body)
        Logger.info("📊 Total URIs available: #{count}")

        :telemetry.execute(
          [:formex_v4, :sparql, :count_complete],
          %{},
          %{total: count}
        )

        {:ok, count}

      {:ok, %{status: status, body: body}} ->
        Logger.error("SPARQL count query failed with status #{status}: #{inspect(body)}")
        {:error, {:http_error, status, body}}

      {:error, reason} ->
        Logger.error("SPARQL count query failed: #{inspect(reason)}")
        {:error, reason}
    end
  end

  @doc """
  Fetches all content stream URIs from the SPARQL endpoint using chunked fetching.

  Uses cursor-based pagination (FILTER with last URI) to fetch results in chunks,
  working around Virtuoso's OFFSET+LIMIT <= 10000 limitation while reducing memory
  usage and improving reliability for large result sets.

  ## Options
    - `:endpoint` - SPARQL endpoint URL (default: Europa Publications endpoint)
    - `:limit` - Optional SPARQL LIMIT for testing (disables chunking, default: nil)
    - `:chunk_size` - Results per chunk (default: 5000)
    - `:timeout` - HTTP timeout in milliseconds (default: 120_000)

  ## Returns
    - `{:ok, list_of_uris}` on success
    - `{:error, reason}` on failure

  ## Examples

      iex> SparqlClient.fetch_uris()
      {:ok, ["http://publications.europa.eu/resource/cellar/...", ...]}

      iex> SparqlClient.fetch_uris(limit: 100)
      {:ok, ["http://publications.europa.eu/resource/cellar/...", ...]}

      iex> SparqlClient.fetch_uris(chunk_size: 1000)
      {:ok, ["http://publications.europa.eu/resource/cellar/...", ...]}
  """
  def fetch_uris(opts \\ []) do
    endpoint = Keyword.get(opts, :endpoint, @default_endpoint)
    limit = Keyword.get(opts, :limit)
    chunk_size = Keyword.get(opts, :chunk_size, @default_chunk_size)
    timeout = Keyword.get(opts, :timeout, @default_timeout)

    Logger.info("🔍 Fetching URIs from SPARQL endpoint: #{endpoint}")

    # If limit is set (for testing), use single-query mode
    if limit do
      Logger.info("   Using LIMIT #{limit} (single query mode)")
      fetch_single_query(endpoint, limit, timeout)
    else
      # Use chunked fetching
      Logger.info("   Using chunked fetching (chunk_size: #{chunk_size})")
      fetch_chunked(endpoint, chunk_size, timeout)
    end
  end

  # Fetches all URIs using cursor-based chunked pagination
  defp fetch_chunked(endpoint, chunk_size, timeout) do
    # First, get the total count
    with {:ok, total_count} <- count_uris(endpoint: endpoint, timeout: timeout) do
      if total_count == 0 do
        Logger.info("✅ No URIs found")
        {:ok, []}
      else
        # Fetch chunks using cursor-based pagination (last URI from previous chunk)
        fetch_all_chunks(endpoint, chunk_size, timeout, total_count, nil, [])
      end
    end
  end

  # Recursively fetches all chunks using cursor-based pagination
  # last_uri is nil for the first chunk, then the last URI from each chunk
  defp fetch_all_chunks(endpoint, chunk_size, timeout, total_count, last_uri, acc) do
    query = build_query_with_cursor(chunk_size, last_uri)
    fetched_so_far = length(acc)

    Logger.info(
      "📦 Fetching chunk: limit=#{chunk_size}, after=\"#{last_uri || "START"}\" (#{fetched_so_far}/#{total_count} fetched)"
    )

    :telemetry.execute(
      [:formex_v4, :sparql, :chunk_start],
      %{},
      %{offset: fetched_so_far, limit: chunk_size, total: total_count}
    )

    request_opts = [
      method: :post,
      url: endpoint,
      headers: [
        {"Accept", "application/sparql-results+json"},
        {"Content-Type", "application/sparql-query"}
      ],
      body: query,
      receive_timeout: timeout,
      retry: :transient,
      max_retries: 3
    ]

    case Req.request(request_opts) do
      {:ok, %{status: 200, body: body}} ->
        uris = parse_sparql_response(body)
        chunk_count = length(uris)

        Logger.info("   ✓ Received #{chunk_count} URIs in this chunk")

        :telemetry.execute(
          [:formex_v4, :sparql, :chunk_complete],
          %{},
          %{count: chunk_count, offset: fetched_so_far, total: total_count}
        )

        handle_chunk_result(uris, acc, endpoint, chunk_size, timeout, total_count)

      {:ok, %{status: status, body: body}} ->
        Logger.error("SPARQL chunk query failed with status #{status}: #{inspect(body)}")
        {:error, {:http_error, status, body}}

      {:error, reason} ->
        Logger.error("SPARQL chunk query failed: #{inspect(reason)}")
        {:error, reason}
    end
  end

  # Handles the result of a chunk fetch and determines next action
  defp handle_chunk_result([], acc, _endpoint, _chunk_size, _timeout, _total_count) do
    Logger.info("✅ Fetched all #{length(acc)} URIs from SPARQL (empty chunk)")
    {:ok, acc}
  end

  defp handle_chunk_result(uris, acc, endpoint, chunk_size, timeout, total_count) do
    new_acc = acc ++ uris
    new_last_uri = List.last(uris)

    # If we've fetched everything, we're done
    if length(new_acc) >= total_count do
      Logger.info("✅ Fetched all #{length(new_acc)} URIs from SPARQL")
      {:ok, new_acc}
    else
      # Continue with next chunk using the last URI as cursor
      fetch_all_chunks(
        endpoint,
        chunk_size,
        timeout,
        total_count,
        new_last_uri,
        new_acc
      )
    end
  end

  # Single-query mode for testing with LIMIT
  defp fetch_single_query(endpoint, limit, timeout) do
    query = build_query_with_limit(limit)

    request_opts = [
      method: :post,
      url: endpoint,
      headers: [
        {"Accept", "application/sparql-results+json"},
        {"Content-Type", "application/sparql-query"}
      ],
      body: query,
      receive_timeout: timeout,
      retry: :transient,
      max_retries: 3
    ]

    case Req.request(request_opts) do
      {:ok, %{status: 200, body: body}} ->
        uris = parse_sparql_response(body)
        Logger.info("✅ Found #{length(uris)} content streams from SPARQL")
        {:ok, uris}

      {:ok, %{status: status, body: body}} ->
        Logger.error("SPARQL query failed with status #{status}: #{inspect(body)}")
        {:error, {:http_error, status, body}}

      {:error, reason} ->
        Logger.error("SPARQL query failed: #{inspect(reason)}")
        {:error, reason}
    end
  end

  # Builds the SPARQL query with LIMIT only (for testing)
  defp build_query_with_limit(limit) when is_integer(limit) and limit > 0 do
    @sparql_query_base
    |> String.replace("<<FILTER>>", "")
    |> String.trim()
    |> Kernel.<>("\nLIMIT #{limit}")
  end

  # Builds the SPARQL query with cursor-based pagination (FILTER + LIMIT)
  # Uses FILTER(?item > "last_uri") to skip already-fetched results
  defp build_query_with_cursor(limit, nil) when is_integer(limit) and limit > 0 do
    # First chunk - no filter needed
    @sparql_query_base
    |> String.replace("<<FILTER>>", "")
    |> String.trim()
    |> Kernel.<>("\nLIMIT #{limit}")
  end

  defp build_query_with_cursor(limit, last_uri)
       when is_integer(limit) and limit > 0 and is_binary(last_uri) do
    # Subsequent chunks - filter to get URIs after the last one
    # Need to escape the URI for SPARQL string literal
    escaped_uri = escape_sparql_string(last_uri)

    filter = "FILTER(?item > \"#{escaped_uri}\")"

    @sparql_query_base
    |> String.replace("<<FILTER>>", filter)
    |> String.trim()
    |> Kernel.<>("\nLIMIT #{limit}")
  end

  # Escapes special characters in SPARQL string literals
  defp escape_sparql_string(str) do
    str
    |> String.replace("\\", "\\\\")
    |> String.replace("\"", "\\\"")
    |> String.replace("\n", "\\n")
    |> String.replace("\r", "\\r")
    |> String.replace("\t", "\\t")
  end

  # Parses SPARQL JSON results format and extracts URIs.
  defp parse_sparql_response(body) when is_map(body) do
    case get_in(body, ["results", "bindings"]) do
      bindings when is_list(bindings) ->
        bindings
        |> Enum.map(fn binding -> get_in(binding, ["item", "value"]) end)
        |> Enum.filter(&is_binary/1)

      _ ->
        []
    end
  end

  defp parse_sparql_response(_), do: []

  # Parses SPARQL COUNT query response and extracts the count value.
  defp parse_count_response(body) when is_map(body) do
    case get_in(body, ["results", "bindings"]) do
      [%{"count" => %{"value" => count_str}}] when is_binary(count_str) ->
        String.to_integer(count_str)

      _ ->
        0
    end
  end

  defp parse_count_response(_), do: 0
end
