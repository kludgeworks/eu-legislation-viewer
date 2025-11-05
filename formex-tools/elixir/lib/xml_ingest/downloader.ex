defmodule XmlIngest.Downloader do
  @moduledoc """
  HTTP downloader for XML files from Europa Publications.
  Integrates with FileCache for persistent storage and deduplication.
  """

  require Logger

  alias XmlIngest.FileCache

  @default_timeout 120_000
  @default_max_retries 3

  @doc """
  Downloads content from a URI, using cache when available.

  ## Options
    - `:timeout` - HTTP timeout in milliseconds (default: 120_000)
    - `:max_retries` - Maximum retry attempts (default: 3)
    - `:force` - Force download even if cached (default: false)

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
    force = Keyword.get(opts, :force, false)

    cond do
      not force and FileCache.cached?(uri) ->
        Logger.debug("Using cached content for: #{uri}")
        FileCache.get(uri)

      true ->
        download_and_cache(uri, timeout, max_retries)
    end
  end

  @doc """
  Downloads multiple URIs concurrently.

  ## Options
    - `:concurrency` - Number of concurrent downloads (default: 12)
    - `:timeout` - HTTP timeout per request in milliseconds (default: 120_000)
    - `:max_retries` - Maximum retry attempts per request (default: 3)
    - `:on_progress` - Progress callback function (default: nil)

  ## Returns
    - `{:ok, results}` where results is a list of `{uri, {:ok, content} | {:error, reason}}`

  ## Examples

      iex> Downloader.download_batch(["http://example.com/1.xml", "http://example.com/2.xml"])
      {:ok, [
        {"http://example.com/1.xml", {:ok, "<xml>...</xml>"}},
        {"http://example.com/2.xml", {:ok, "<xml>...</xml>"}}
      ]}
  """
  def download_batch(uris, opts \\ []) do
    concurrency = Keyword.get(opts, :concurrency, 12)
    timeout = Keyword.get(opts, :timeout, @default_timeout)
    max_retries = Keyword.get(opts, :max_retries, @default_max_retries)
    on_progress = Keyword.get(opts, :on_progress)

    Logger.info("📥 Downloading #{length(uris)} files (concurrency: #{concurrency})")

    results =
      uris
      |> Task.async_stream(
        fn uri ->
          result = download(uri, timeout: timeout, max_retries: max_retries)
          if on_progress, do: on_progress.(uri, result)
          {uri, result}
        end,
        max_concurrency: concurrency,
        timeout: timeout + 5_000,
        on_timeout: :kill_task
      )
      |> Enum.map(fn
        {:ok, result} -> result
        {:exit, reason} -> {:error, {:task_exit, reason}}
      end)

    success_count = Enum.count(results, fn {_uri, result} -> match?({:ok, _}, result) end)
    error_count = length(results) - success_count

    Logger.info("✅ Download complete: #{success_count} succeeded, #{error_count} failed")

    {:ok, results}
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
        case FileCache.put(uri, body) do
          :ok -> {:ok, body}
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

  @doc """
  Filters URIs to find which ones need downloading (not cached).

  ## Examples

      iex> Downloader.filter_uncached(["http://example.com/1.xml", "http://example.com/2.xml"])
      ["http://example.com/2.xml"]  # Assuming 1.xml is cached
  """
  def filter_uncached(uris) when is_list(uris) do
    Enum.reject(uris, &FileCache.cached?/1)
  end

  @doc """
  Gets download statistics.

  ## Examples

      iex> Downloader.stats(["http://example.com/1.xml", "http://example.com/2.xml"])
      %{total: 2, cached: 1, to_download: 1}
  """
  def stats(uris) when is_list(uris) do
    total = length(uris)
    to_download = length(filter_uncached(uris))
    cached = total - to_download

    %{
      total: total,
      cached: cached,
      to_download: to_download
    }
  end
end
