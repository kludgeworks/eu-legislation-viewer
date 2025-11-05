defmodule FormexV4.CacheTest do
  use ExUnit.Case, async: false

  alias FormexV4.Cache

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

    Cache.init()
    :ok
  end

  describe "init/0" do
    test "creates cache directory if it doesn't exist" do
      File.rm_rf!(@test_cache_dir)
      refute File.exists?(@test_cache_dir)

      Cache.init()
      assert File.exists?(@test_cache_dir)
    end

    test "succeeds if directory already exists" do
      assert File.exists?(@test_cache_dir)
      assert Cache.init() == :ok
    end
  end

  describe "cache_path/1" do
    test "returns full path to cache file" do
      path = Cache.cache_path(@test_uri)
      assert String.starts_with?(path, @test_cache_dir)
      assert String.match?(path, ~r/[a-f0-9]{64}$/)
    end
  end

  describe "cached?/1" do
    test "returns false for uncached URI" do
      refute Cache.cached?(@test_uri)
    end

    test "returns true for cached URI" do
      Cache.put(@test_uri, @test_content)
      assert Cache.cached?(@test_uri)
    end
  end

  describe "put/2 and get/1" do
    test "stores and retrieves content" do
      assert Cache.put(@test_uri, @test_content) == :ok
      assert Cache.get(@test_uri) == {:ok, @test_content}
    end

    test "returns error for uncached URI" do
      assert Cache.get(@test_uri) == {:error, :not_found}
    end

    test "overwrites existing content" do
      Cache.put(@test_uri, "old content")
      Cache.put(@test_uri, "new content")

      assert Cache.get(@test_uri) == {:ok, "new content"}
    end
  end

  describe "delete/1" do
    test "removes cached content" do
      Cache.put(@test_uri, @test_content)
      assert Cache.cached?(@test_uri)

      assert Cache.delete(@test_uri) == :ok
      refute Cache.cached?(@test_uri)
    end

    test "succeeds even if file doesn't exist" do
      assert Cache.delete(@test_uri) == :ok
    end
  end
end
