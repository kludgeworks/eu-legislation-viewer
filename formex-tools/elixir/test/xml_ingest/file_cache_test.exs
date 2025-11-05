defmodule XmlIngest.FileCacheTest do
  use ExUnit.Case, async: false

  alias XmlIngest.FileCache

  @test_cache_dir ".cache/test-formex-sync"
  @test_uri "http://example.com/test.xml"
  @test_content "<xml>test content</xml>"

  setup do
    # Set test cache directory
    System.put_env("CACHE_DIR", @test_cache_dir)

    # Clean up before each test
    on_exit(fn ->
      File.rm_rf(@test_cache_dir)
      System.delete_env("CACHE_DIR")
    end)

    FileCache.init()
    :ok
  end

  describe "init/0" do
    test "creates cache directory if it doesn't exist" do
      File.rm_rf!(@test_cache_dir)
      refute File.exists?(@test_cache_dir)

      FileCache.init()
      assert File.exists?(@test_cache_dir)
    end

    test "succeeds if directory already exists" do
      assert File.exists?(@test_cache_dir)
      assert FileCache.init() == :ok
    end
  end

  describe "cache_key/1" do
    test "generates consistent hash for same URI" do
      key1 = FileCache.cache_key(@test_uri)
      key2 = FileCache.cache_key(@test_uri)

      assert key1 == key2
      assert is_binary(key1)
      assert String.length(key1) == 64  # SHA-256 hex = 64 chars
    end

    test "generates different hashes for different URIs" do
      key1 = FileCache.cache_key("http://example.com/file1.xml")
      key2 = FileCache.cache_key("http://example.com/file2.xml")

      assert key1 != key2
    end

    test "generates filesystem-safe keys" do
      key = FileCache.cache_key(@test_uri)
      assert String.match?(key, ~r/^[a-f0-9]+$/)
    end
  end

  describe "cache_path/1" do
    test "returns full path to cache file" do
      path = FileCache.cache_path(@test_uri)
      assert String.starts_with?(path, @test_cache_dir)
      assert String.ends_with?(path, FileCache.cache_key(@test_uri))
    end
  end

  describe "cached?/1" do
    test "returns false for uncached URI" do
      refute FileCache.cached?(@test_uri)
    end

    test "returns true for cached URI" do
      FileCache.put(@test_uri, @test_content)
      assert FileCache.cached?(@test_uri)
    end
  end

  describe "put/2 and get/1" do
    test "stores and retrieves content" do
      assert FileCache.put(@test_uri, @test_content) == :ok
      assert FileCache.get(@test_uri) == {:ok, @test_content}
    end

    test "returns error for uncached URI" do
      assert FileCache.get(@test_uri) == {:error, :not_found}
    end

    test "overwrites existing content" do
      FileCache.put(@test_uri, "old content")
      FileCache.put(@test_uri, "new content")

      assert FileCache.get(@test_uri) == {:ok, "new content"}
    end
  end

  describe "delete/1" do
    test "removes cached content" do
      FileCache.put(@test_uri, @test_content)
      assert FileCache.cached?(@test_uri)

      assert FileCache.delete(@test_uri) == :ok
      refute FileCache.cached?(@test_uri)
    end

    test "succeeds even if file doesn't exist" do
      assert FileCache.delete(@test_uri) == :ok
    end
  end

  describe "clear/0" do
    test "removes all cached files" do
      FileCache.put("http://example.com/file1.xml", "content1")
      FileCache.put("http://example.com/file2.xml", "content2")
      FileCache.put("http://example.com/file3.xml", "content3")

      assert FileCache.clear() == :ok

      refute FileCache.cached?("http://example.com/file1.xml")
      refute FileCache.cached?("http://example.com/file2.xml")
      refute FileCache.cached?("http://example.com/file3.xml")
    end

    test "recreates cache directory after clearing" do
      FileCache.clear()
      assert File.exists?(@test_cache_dir)
    end
  end

  describe "stats/0" do
    test "returns zero stats for empty cache" do
      assert {:ok, %{count: 0, size_bytes: 0}} = FileCache.stats()
    end

    test "returns correct file count" do
      FileCache.put("http://example.com/file1.xml", "content1")
      FileCache.put("http://example.com/file2.xml", "content2")
      FileCache.put("http://example.com/file3.xml", "content3")

      assert {:ok, %{count: 3, size_bytes: size}} = FileCache.stats()
      assert size > 0
    end

    test "returns correct total size" do
      content = String.duplicate("x", 1000)
      FileCache.put(@test_uri, content)

      assert {:ok, %{count: 1, size_bytes: size}} = FileCache.stats()
      assert size == 1000
    end
  end
end
