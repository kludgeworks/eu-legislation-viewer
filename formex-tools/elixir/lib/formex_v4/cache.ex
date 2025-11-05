defmodule FormexV4.Cache do
  @moduledoc """
  File-based cache for downloaded XML documents.
  Uses SHA-256 hashing of URIs for filesystem-safe cache keys.
  """

  require Logger

  @default_cache_dir ".cache/formex-v4"

  @doc """
  Gets the cache directory path.
  Can be overridden with CACHE_DIR environment variable.
  """
  def cache_dir do
    System.get_env("CACHE_DIR", @default_cache_dir)
  end

  @doc """
  Initializes the cache directory.
  Creates the directory if it doesn't exist.
  """
  def init do
    dir = cache_dir()
    File.mkdir_p!(dir)
    Logger.debug("Initialized cache directory: #{dir}")
    :ok
  end

  # Generates a cache key (filename) for a given URI.
  # Uses SHA-256 hash of the URI for filesystem-safe keys.
  defp cache_key(uri) when is_binary(uri) do
    :crypto.hash(:sha256, uri)
    |> Base.encode16(case: :lower)
  end

  @doc """
  Gets the full cache file path for a given URI.

  ## Examples

      iex> FileCache.cache_path("http://example.com/file.xml")
      ".cache/formex-sync/a1b2c3d4e5f6..."
  """
  def cache_path(uri) when is_binary(uri) do
    Path.join(cache_dir(), cache_key(uri))
  end

  @doc """
  Checks if a URI is cached.

  ## Examples

      iex> FileCache.cached?("http://example.com/file.xml")
      true
  """
  def cached?(uri) when is_binary(uri) do
    cache_path(uri)
    |> File.exists?()
  end

  @doc """
  Reads cached content for a URI.

  ## Returns
    - `{:ok, content}` if cached
    - `{:error, :not_found}` if not cached
    - `{:error, reason}` on other errors

  ## Examples

      iex> FileCache.get("http://example.com/file.xml")
      {:ok, "<xml>...</xml>"}
  """
  def get(uri) when is_binary(uri) do
    path = cache_path(uri)

    case File.read(path) do
      {:ok, content} ->
        Logger.debug("Cache hit: #{uri}")
        {:ok, content}

      {:error, :enoent} ->
        Logger.debug("Cache miss: #{uri}")
        {:error, :not_found}

      {:error, reason} = error ->
        Logger.error("Cache read error for #{uri}: #{inspect(reason)}")
        error
    end
  end

  @doc """
  Writes content to cache for a URI.

  ## Examples

      iex> FileCache.put("http://example.com/file.xml", "<xml>...</xml>")
      :ok
  """
  def put(uri, content) when is_binary(uri) and is_binary(content) do
    path = cache_path(uri)

    case File.write(path, content) do
      :ok ->
        Logger.debug("Cached: #{uri}")
        :ok

      {:error, reason} = error ->
        Logger.error("Cache write error for #{uri}: #{inspect(reason)}")
        error
    end
  end

  @doc """
  Deletes cached content for a URI.

  ## Examples

      iex> FileCache.delete("http://example.com/file.xml")
      :ok
  """
  def delete(uri) when is_binary(uri) do
    path = cache_path(uri)

    case File.rm(path) do
      :ok ->
        Logger.debug("Deleted from cache: #{uri}")
        :ok

      {:error, :enoent} ->
        :ok

      {:error, reason} = error ->
        Logger.error("Cache delete error for #{uri}: #{inspect(reason)}")
        error
    end
  end
end
