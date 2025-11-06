defmodule FormexV4.Sparql.ClientTest do
  use ExUnit.Case, async: true

  alias FormexV4.Sparql.Client

  # Note: These tests use mocked HTTP responses to avoid network dependencies
  # Integration tests with the real Europa endpoint should be run separately

  setup do
    # Start a simple HTTP server mock using Bypass
    bypass = Bypass.open()
    {:ok, bypass: bypass}
  end

  describe "fetch_uris/1" do
    test "fetches all URIs without LIMIT", %{bypass: bypass} do
      Bypass.expect_once(bypass, "POST", "/sparql", fn conn ->
        {:ok, body, conn} = Plug.Conn.read_body(conn)
        assert body =~ "SELECT DISTINCT ?item"
        assert body =~ "ORDER BY ?item"
        refute body =~ "LIMIT"

        response = %{
          "results" => %{
            "bindings" => [
              %{"item" => %{"type" => "uri", "value" => "http://example.com/item1"}},
              %{"item" => %{"type" => "uri", "value" => "http://example.com/item2"}},
              %{"item" => %{"type" => "uri", "value" => "http://example.com/item3"}}
            ]
          }
        }

        conn
        |> Plug.Conn.put_resp_content_type("application/sparql-results+json")
        |> Plug.Conn.resp(200, Jason.encode!(response))
      end)

      endpoint = "http://localhost:#{bypass.port}/sparql"
      assert {:ok, uris} = Client.fetch_uris(endpoint: endpoint, timeout: 5000)
      assert length(uris) == 3
      assert "http://example.com/item1" in uris
      assert "http://example.com/item2" in uris
      assert "http://example.com/item3" in uris
    end

    test "fetches URIs with LIMIT", %{bypass: bypass} do
      Bypass.expect_once(bypass, "POST", "/sparql", fn conn ->
        {:ok, body, conn} = Plug.Conn.read_body(conn)
        assert body =~ "SELECT DISTINCT ?item"
        assert body =~ "ORDER BY ?item"
        assert body =~ "LIMIT 10"

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

    test "handles empty results", %{bypass: bypass} do
      Bypass.expect_once(bypass, "POST", "/sparql", fn conn ->
        response = %{"results" => %{"bindings" => []}}

        conn
        |> Plug.Conn.put_resp_content_type("application/sparql-results+json")
        |> Plug.Conn.resp(200, Jason.encode!(response))
      end)

      endpoint = "http://localhost:#{bypass.port}/sparql"
      assert {:ok, []} = Client.fetch_uris(endpoint: endpoint, timeout: 5000)
    end

    test "returns error when HTTP request fails", %{bypass: bypass} do
      # Note: Req has retry logic, so expect multiple attempts
      Bypass.expect(bypass, "POST", "/sparql", fn conn ->
        Plug.Conn.resp(conn, 500, "Internal Server Error")
      end)

      endpoint = "http://localhost:#{bypass.port}/sparql"

      assert {:error, {:http_error, 500, _}} =
               Client.fetch_uris(endpoint: endpoint, timeout: 5000)
    end
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
end
