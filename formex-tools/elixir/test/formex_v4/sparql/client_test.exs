defmodule FormexV4.Sparql.ClientTest do
  use ExUnit.Case, async: true

  alias FormexV4.Sparql.Client

  # Note: These tests use mocked HTTP responses to avoid network dependencies
  # Integration tests with the real Europa endpoint should be run separately

  setup do
    # Start a simple HTTP server mock using Req.Test
    bypass = Bypass.open()
    {:ok, bypass: bypass}
  end

  describe "count_uris/1" do
    test "returns count from SPARQL COUNT query", %{bypass: bypass} do
      Bypass.expect_once(bypass, "POST", "/sparql", fn conn ->
        # Verify the request body contains COUNT query
        {:ok, body, conn} = Plug.Conn.read_body(conn)
        assert body =~ "COUNT(DISTINCT ?item)"
        assert body =~ "cmr:manifestationMimeType"

        # Return mock count response
        response = %{
          "results" => %{
            "bindings" => [
              %{"count" => %{"type" => "literal", "value" => "12345"}}
            ]
          }
        }

        conn
        |> Plug.Conn.put_resp_content_type("application/sparql-results+json")
        |> Plug.Conn.resp(200, Jason.encode!(response))
      end)

      endpoint = "http://localhost:#{bypass.port}/sparql"
      assert {:ok, 12_345} = Client.count_uris(endpoint: endpoint, timeout: 5000)
    end

    test "returns 0 when count query returns empty results", %{bypass: bypass} do
      Bypass.expect_once(bypass, "POST", "/sparql", fn conn ->
        response = %{"results" => %{"bindings" => []}}

        conn
        |> Plug.Conn.put_resp_content_type("application/sparql-results+json")
        |> Plug.Conn.resp(200, Jason.encode!(response))
      end)

      endpoint = "http://localhost:#{bypass.port}/sparql"
      assert {:ok, 0} = Client.count_uris(endpoint: endpoint, timeout: 5000)
    end

    test "returns error when HTTP request fails", %{bypass: bypass} do
      # Note: Req has retry logic, so expect multiple attempts
      Bypass.expect(bypass, "POST", "/sparql", fn conn ->
        Plug.Conn.resp(conn, 500, "Internal Server Error")
      end)

      endpoint = "http://localhost:#{bypass.port}/sparql"

      assert {:error, {:http_error, 500, _}} =
               Client.count_uris(endpoint: endpoint, timeout: 5000)
    end
  end

  describe "fetch_uris/1 with limit (single query mode)" do
    test "fetches URIs with LIMIT clause", %{bypass: bypass} do
      Bypass.expect_once(bypass, "POST", "/sparql", fn conn ->
        {:ok, body, conn} = Plug.Conn.read_body(conn)
        assert body =~ "SELECT DISTINCT ?item"
        assert body =~ "ORDER BY ?item"
        assert body =~ "LIMIT 10"
        refute body =~ "OFFSET"

        response = %{
          "results" => %{
            "bindings" => [
              %{"item" => %{"type" => "uri", "value" => "http://example.com/item1"}},
              %{"item" => %{"type" => "uri", "value" => "http://example.com/item2"}}
            ]
          }
        }

        conn
        |> Plug.Conn.put_resp_content_type("application/sparql-results+json")
        |> Plug.Conn.resp(200, Jason.encode!(response))
      end)

      endpoint = "http://localhost:#{bypass.port}/sparql"
      assert {:ok, uris} = Client.fetch_uris(endpoint: endpoint, limit: 10, timeout: 5000)
      assert length(uris) == 2
      assert "http://example.com/item1" in uris
      assert "http://example.com/item2" in uris
    end

    test "handles empty results with LIMIT", %{bypass: bypass} do
      Bypass.expect_once(bypass, "POST", "/sparql", fn conn ->
        response = %{"results" => %{"bindings" => []}}

        conn
        |> Plug.Conn.put_resp_content_type("application/sparql-results+json")
        |> Plug.Conn.resp(200, Jason.encode!(response))
      end)

      endpoint = "http://localhost:#{bypass.port}/sparql"
      assert {:ok, []} = Client.fetch_uris(endpoint: endpoint, limit: 100, timeout: 5000)
    end
  end

  describe "fetch_uris/1 with chunking (default mode)" do
    test "fetches URIs in chunks using cursor-based pagination", %{bypass: bypass} do
      # First request: COUNT query
      Bypass.expect(bypass, "POST", "/sparql", fn conn ->
        {:ok, body, conn} = Plug.Conn.read_body(conn)

        response =
          if body =~ "COUNT(DISTINCT ?item)" do
            # Return count of 7 (will need 2 chunks with chunk_size=5)
            %{"results" => %{"bindings" => [%{"count" => %{"value" => "7"}}]}}
          else
            # Determine which chunk based on FILTER
            cond do
              # First chunk - no filter
              not String.contains?(body, "FILTER") ->
                %{
                  "results" => %{
                    "bindings" =>
                      Enum.map(1..5, fn i ->
                        %{"item" => %{"value" => "http://example.com/item#{i}"}}
                      end)
                  }
                }

              # Second chunk - filter after item5
              body =~ "FILTER(?item > \"http://example.com/item5\")" ->
                %{
                  "results" => %{
                    "bindings" =>
                      Enum.map(6..7, fn i ->
                        %{"item" => %{"value" => "http://example.com/item#{i}"}}
                      end)
                  }
                }

              true ->
                %{"results" => %{"bindings" => []}}
            end
          end

        conn
        |> Plug.Conn.put_resp_content_type("application/sparql-results+json")
        |> Plug.Conn.resp(200, Jason.encode!(response))
      end)

      endpoint = "http://localhost:#{bypass.port}/sparql"
      assert {:ok, uris} = Client.fetch_uris(endpoint: endpoint, chunk_size: 5, timeout: 5000)
      assert length(uris) == 7
      assert "http://example.com/item1" in uris
      assert "http://example.com/item7" in uris
    end

    test "handles single chunk when total < chunk_size", %{bypass: bypass} do
      Bypass.expect(bypass, "POST", "/sparql", fn conn ->
        {:ok, body, conn} = Plug.Conn.read_body(conn)

        response =
          if body =~ "COUNT(DISTINCT ?item)" do
            %{"results" => %{"bindings" => [%{"count" => %{"value" => "3"}}]}}
          else
            %{
              "results" => %{
                "bindings" =>
                  Enum.map(1..3, fn i ->
                    %{"item" => %{"value" => "http://example.com/item#{i}"}}
                  end)
              }
            }
          end

        conn
        |> Plug.Conn.put_resp_content_type("application/sparql-results+json")
        |> Plug.Conn.resp(200, Jason.encode!(response))
      end)

      endpoint = "http://localhost:#{bypass.port}/sparql"
      assert {:ok, uris} = Client.fetch_uris(endpoint: endpoint, chunk_size: 10, timeout: 5000)
      assert length(uris) == 3
    end

    test "handles empty result set", %{bypass: bypass} do
      Bypass.expect_once(bypass, "POST", "/sparql", fn conn ->
        {:ok, body, conn} = Plug.Conn.read_body(conn)
        assert body =~ "COUNT(DISTINCT ?item)"

        response = %{"results" => %{"bindings" => [%{"count" => %{"value" => "0"}}]}}

        conn
        |> Plug.Conn.put_resp_content_type("application/sparql-results+json")
        |> Plug.Conn.resp(200, Jason.encode!(response))
      end)

      endpoint = "http://localhost:#{bypass.port}/sparql"
      assert {:ok, []} = Client.fetch_uris(endpoint: endpoint, timeout: 5000)
    end

    test "returns error when count query fails", %{bypass: bypass} do
      # Note: Req has retry logic, so expect multiple attempts
      Bypass.expect(bypass, "POST", "/sparql", fn conn ->
        Plug.Conn.resp(conn, 500, "Internal Server Error")
      end)

      endpoint = "http://localhost:#{bypass.port}/sparql"

      assert {:error, {:http_error, 500, _}} =
               Client.fetch_uris(endpoint: endpoint, timeout: 5000)
    end

    test "returns error when chunk query fails", %{bypass: bypass} do
      Bypass.expect(bypass, "POST", "/sparql", fn conn ->
        {:ok, body, conn} = Plug.Conn.read_body(conn)

        if body =~ "COUNT(DISTINCT ?item)" do
          response = %{"results" => %{"bindings" => [%{"count" => %{"value" => "10"}}]}}

          conn
          |> Plug.Conn.put_resp_content_type("application/sparql-results+json")
          |> Plug.Conn.resp(200, Jason.encode!(response))
        else
          # Fail on chunk query
          Plug.Conn.resp(conn, 500, "Internal Server Error")
        end
      end)

      endpoint = "http://localhost:#{bypass.port}/sparql"

      assert {:error, {:http_error, 500, _}} =
               Client.fetch_uris(endpoint: endpoint, timeout: 5000)
    end
  end
end
