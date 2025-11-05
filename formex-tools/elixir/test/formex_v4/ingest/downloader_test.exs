defmodule FormexV4.Ingest.DownloaderTest do
  use ExUnit.Case, async: false

  alias FormexV4.Ingest.Downloader
  alias FormexV4.Cache

  @test_cache_dir ".cache/test-downloader"
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

  describe "download/2 with cache" do
    test "uses cached content when available" do
      # Pre-cache content
      Cache.put(@test_uri, @test_content)

      # Download should return cached content without network call
      assert Downloader.download(@test_uri) == {:ok, @test_content}
    end
  end

  # Note: Actual network download tests are not included here as they require
  # network access or complex mocking. Integration tests should be added separately.
end
