defmodule XmlIngest.SparqlClient do
  @moduledoc """
  Client for querying the Europa Publications SPARQL endpoint.
  Fetches content stream URIs for XML documents with type=fmx4.
  """

  require Logger

  @default_endpoint "https://publications.europa.eu/webapi/rdf/sparql"

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
  """

  @doc """
  Fetches all content stream URIs from the SPARQL endpoint.

  ## Options
    - `:endpoint` - SPARQL endpoint URL (default: Europa Publications endpoint)
    - `:limit` - Optional SPARQL LIMIT clause for testing (default: nil)
    - `:timeout` - HTTP timeout in milliseconds (default: 120_000)

  ## Returns
    - `{:ok, list_of_uris}` on success
    - `{:error, reason}` on failure

  ## Examples

      iex> SparqlClient.fetch_uris()
      {:ok, ["http://publications.europa.eu/resource/cellar/...", ...]}

      iex> SparqlClient.fetch_uris(limit: 100)
      {:ok, ["http://publications.europa.eu/resource/cellar/...", ...]}
  """
  def fetch_uris(opts \\ []) do
    endpoint = Keyword.get(opts, :endpoint, @default_endpoint)
    limit = Keyword.get(opts, :limit)
    timeout = Keyword.get(opts, :timeout, 120_000)

    query = build_query(limit)

    Logger.info("🔍 Querying SPARQL endpoint: #{endpoint}")
    if limit, do: Logger.info("   Using LIMIT #{limit}")

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

  # Builds the SPARQL query with optional LIMIT clause.
  defp build_query(nil), do: @sparql_query

  defp build_query(limit) when is_integer(limit) and limit > 0 do
    String.trim(@sparql_query) <> "\nLIMIT #{limit}"
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
end
