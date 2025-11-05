defmodule FormexV4.Ingest.Downloader do
  @moduledoc """
  HTTP downloader for XML files from Europa Publications.
  Integrates with Cache for persistent storage and deduplication.
  """

  require Logger

  alias FormexV4.Cache

  @default_timeout 120_000
  @default_max_retries 3

  @doc """
  Downloads content from a URI, using cache when available.

  ## Options
    - `:timeout` - HTTP timeout in milliseconds (default: 120_000)
    - `:max_retries` - Maximum retry attempts (default: 3)

  ## Returns
    - `{:ok, content}` on success
    - `{:error, reason}` on failure

  ## Examples

      iex> Downloader.download("http://example.com/file.xml")
      {:ok, "<xml>...</xml>"}
  """
  def download(uri, opts \\ []) do
    timeout = Keyword.get(opts, :timeout, @default_timeout)
    max_retries = Keyword.get(opts, :max_retries, @default_max_retries)

    # Emit telemetry: download start
    :telemetry.execute([:formex_v4, :downloader, :start], %{}, %{uri: uri})

    result =
      if Cache.cached?(uri) do
        Logger.debug("Using cached content for: #{uri}")

        # Emit telemetry: cache hit
        :telemetry.execute([:formex_v4, :downloader, :cache_hit], %{}, %{uri: uri})

        Cache.get(uri)
      else
        download_and_cache(uri, timeout, max_retries)
      end

    # Emit telemetry: download complete
    :telemetry.execute(
      [:formex_v4, :downloader, :complete],
      %{},
      %{uri: uri, cached: Cache.cached?(uri)}
    )

    result
  end

  # Private helper: downloads and caches content
  defp download_and_cache(uri, timeout, max_retries) do
    Logger.debug("Downloading: #{uri}")

    request_opts = [
      method: :get,
      url: uri,
      # Don't set Accept header - Europa server returns 406 with specific Accept headers
      receive_timeout: timeout,
      retry: :transient,
      max_retries: max_retries
    ]

    case Req.request(request_opts) do
      {:ok, %{status: 200, body: body}} when is_binary(body) ->
        # Cache the content
        case Cache.put(uri, body) do
          :ok ->
            {:ok, body}

          {:error, reason} ->
            Logger.warning("Failed to cache #{uri}: #{inspect(reason)}")
            # Return content even if caching fails
            {:ok, body}
        end

      {:ok, %{status: status}} ->
        Logger.error("HTTP #{status} for #{uri}")
        {:error, {:http_error, status}}

      {:error, reason} ->
        Logger.error("Download failed for #{uri}: #{inspect(reason)}")
        {:error, reason}
    end
  end
end
