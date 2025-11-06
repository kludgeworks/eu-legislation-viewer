defmodule FormexV4.Sparql.Client do
  @moduledoc """
  Client for querying the Europa Publications SPARQL endpoint.
  Fetches content stream URIs for XML documents with type=fmx4.
  Uses a single query to fetch all URIs.
  """

  require Logger

  @default_endpoint "https://publications.europa.eu/webapi/rdf/sparql"
  @default_timeout 300_000

  @sparql_query """
  PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
  PREFIX cmr: <http://publications.europa.eu/ontology/cdm/cmr#>
  PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

  SELECT DISTINCT ?item
  WHERE {
    ?expression cmr:lang "eng"^^xsd:language .
    ?manifestation cdm:manifestation_manifests_expression ?expression .
    ?item cdm:item_belongs_to_manifestation ?manifestation .
    ?item cmr:manifestationMimeType "application/xml;type=fmx4" .
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
  Fetches all content stream URIs from the SPARQL endpoint.

  ## Options
    - `:endpoint` - SPARQL endpoint URL (default: Europa Publications endpoint)
    - `:limit` - Optional SPARQL LIMIT for testing (default: nil)
    - `:timeout` - HTTP timeout in milliseconds (default: 300_000)

  ## Returns
    - `{:ok, list_of_uris}` on success
    - `{:error, reason}` on failure

  ## Examples

      iex> Client.fetch_uris()
      {:ok, ["http://publications.europa.eu/resource/cellar/...", ...]}

      iex> Client.fetch_uris(limit: 100)
      {:ok, ["http://publications.europa.eu/resource/cellar/...", ...]}
  """
  def fetch_uris(opts \\ []) do
    endpoint = Keyword.get(opts, :endpoint, @default_endpoint)
    limit = Keyword.get(opts, :limit)
    timeout = Keyword.get(opts, :timeout, @default_timeout)

    query =
      if limit do
        @sparql_query <> "\nLIMIT #{limit}"
      else
        @sparql_query
      end

    Logger.info("🔍 Fetching URIs from SPARQL endpoint: #{endpoint}")

    if limit do
      Logger.info("   Using LIMIT #{limit}")
    end

    :telemetry.execute([:formex_v4, :sparql, :fetch_start], %{}, %{})

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

        :telemetry.execute(
          [:formex_v4, :sparql, :fetch_complete],
          %{},
          %{count: length(uris)}
        )

        {:ok, uris}

      {:ok, %{status: status, body: body}} ->
        Logger.error("SPARQL query failed with status #{status}: #{inspect(body)}")
        {:error, {:http_error, status, body}}

      {:error, reason} ->
        Logger.error("SPARQL query failed: #{inspect(reason)}")
        {:error, reason}
    end
  end

  @doc """
  Counts total number of URIs available from the SPARQL endpoint.

  ## Options
    - `:endpoint` - SPARQL endpoint URL (default: Europa Publications endpoint)
    - `:timeout` - HTTP timeout in milliseconds (default: 300_000)

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

  # Parses SPARQL JSON results format and extracts URIs
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

  # Parses SPARQL COUNT query response and extracts the count value
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
