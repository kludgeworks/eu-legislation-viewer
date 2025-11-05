defmodule XmlIngest.SparqlClientTest do
  use ExUnit.Case, async: true

  alias XmlIngest.SparqlClient

  describe "parse_sparql_response/1" do
    test "parses valid SPARQL JSON results" do
      response = %{
        "results" => %{
          "bindings" => [
            %{"item" => %{"value" => "http://example.com/1"}},
            %{"item" => %{"value" => "http://example.com/2"}},
            %{"item" => %{"value" => "http://example.com/3"}}
          ]
        }
      }

      assert SparqlClient.parse_sparql_response(response) == [
               "http://example.com/1",
               "http://example.com/2",
               "http://example.com/3"
             ]
    end

    test "handles empty results" do
      response = %{
        "results" => %{
          "bindings" => []
        }
      }

      assert SparqlClient.parse_sparql_response(response) == []
    end

    test "filters out nil values" do
      response = %{
        "results" => %{
          "bindings" => [
            %{"item" => %{"value" => "http://example.com/1"}},
            %{"other" => %{"value" => "http://example.com/2"}},
            %{"item" => %{"value" => "http://example.com/3"}}
          ]
        }
      }

      assert SparqlClient.parse_sparql_response(response) == [
               "http://example.com/1",
               "http://example.com/3"
             ]
    end

    test "handles malformed response" do
      assert SparqlClient.parse_sparql_response(%{}) == []
      assert SparqlClient.parse_sparql_response(nil) == []
      assert SparqlClient.parse_sparql_response("invalid") == []
    end
  end

  describe "build_query/1" do
    test "returns base query without limit" do
      query = SparqlClient.build_query(nil)
      refute String.contains?(query, "LIMIT")
      assert String.contains?(query, "SELECT DISTINCT ?item")
    end

    test "appends LIMIT clause when provided" do
      query = SparqlClient.build_query(100)
      assert String.contains?(query, "LIMIT 100")
      assert String.contains?(query, "SELECT DISTINCT ?item")
    end

    test "handles different limit values" do
      assert String.contains?(SparqlClient.build_query(1), "LIMIT 1")
      assert String.contains?(SparqlClient.build_query(1000), "LIMIT 1000")
    end
  end

  # Note: fetch_uris/1 is not tested here as it requires network access
  # Integration tests should be added separately if needed
end
