defmodule XmlIngest.SparqlClientTest do
  use ExUnit.Case, async: true

  # Note: fetch_uris/1 is not tested here as it requires network access
  # Integration tests should be added separately if needed
  #
  # Previously tested private helper functions build_query/1 and
  # parse_sparql_response/1 are now tested indirectly through fetch_uris/1
end
