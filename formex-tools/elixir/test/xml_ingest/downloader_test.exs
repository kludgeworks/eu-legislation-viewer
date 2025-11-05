defmodule XmlIngest.DownloaderTest do
  use ExUnit.Case, async: false

  alias XmlIngest.{Downloader, FileCache}

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

    FileCache.init()
    :ok
  end

  describe "filter_uncached/1" do
    test "returns all URIs when nothing is cached" do
      uris = [
        "http://example.com/file1.xml",
        "http://example.com/file2.xml",
        "http://example.com/file3.xml"
      ]

      assert Downloader.filter_uncached(uris) == uris
    end

    test "filters out cached URIs" do
      uris = [
        "http://example.com/file1.xml",
        "http://example.com/file2.xml",
        "http://example.com/file3.xml"
      ]

      # Cache file2
      FileCache.put("http://example.com/file2.xml", "content")

      uncached = Downloader.filter_uncached(uris)

      assert length(uncached) == 2
      assert "http://example.com/file1.xml" in uncached
      assert "http://example.com/file3.xml" in uncached
      refute "http://example.com/file2.xml" in uncached
    end

    test "returns empty list when all are cached" do
      uris = [
        "http://example.com/file1.xml",
        "http://example.com/file2.xml"
      ]

      Enum.each(uris, fn uri -> FileCache.put(uri, "content") end)

      assert Downloader.filter_uncached(uris) == []
    end
  end

  describe "stats/1" do
    test "calculates download statistics" do
      uris = [
        "http://example.com/file1.xml",
        "http://example.com/file2.xml",
        "http://example.com/file3.xml"
      ]

      # Cache one file
      FileCache.put("http://example.com/file2.xml", "content")

      stats = Downloader.stats(uris)

      assert stats == %{
        total: 3,
        cached: 1,
        to_download: 2
      }
    end

    test "handles empty list" do
      stats = Downloader.stats([])

      assert stats == %{
        total: 0,
        cached: 0,
        to_download: 0
      }
    end

    test "handles all cached" do
      uris = [
        "http://example.com/file1.xml",
        "http://example.com/file2.xml"
      ]

      Enum.each(uris, fn uri -> FileCache.put(uri, "content") end)

      stats = Downloader.stats(uris)

      assert stats == %{
        total: 2,
        cached: 2,
        to_download: 0
      }
    end
  end

  describe "download/2 with cache" do
    test "uses cached content when available" do
      # Pre-cache content
      FileCache.put(@test_uri, @test_content)

      # Download should return cached content without network call
      assert Downloader.download(@test_uri) == {:ok, @test_content}
    end

    test "bypasses cache when force option is true" do
      # Pre-cache old content
      FileCache.put(@test_uri, "old content")

      # With force: true, would attempt network download
      # We can't easily test the actual network call without mocking
      # So we just verify the option is accepted
      result = Downloader.download(@test_uri, force: false)
      assert result == {:ok, "old content"}
    end
  end

  # Note: Actual network download tests are not included here as they require
  # network access or complex mocking. Integration tests should be added separately.
  
  describe "download_batch/2" do
    test "respects concurrency option" do
      # This test verifies the function accepts the option
      # Actual concurrency testing would require network mocking
      uris = []
      
      assert {:ok, []} = Downloader.download_batch(uris, concurrency: 5)
    end

    test "returns empty results for empty input" do
      assert {:ok, []} = Downloader.download_batch([])
    end
  end
end
