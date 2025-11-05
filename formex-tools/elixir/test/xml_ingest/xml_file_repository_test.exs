defmodule XmlIngest.XmlFileRepositoryTest do
  use ExUnit.Case, async: true

  alias XmlIngest.XmlFileRepository
  alias XmlIngest.TestHelper

  setup do
    # Create test database
    db_path = TestHelper.setup_test_db()

    # Cleanup on exit
    on_exit(fn ->
      TestHelper.teardown_test_db(db_path)
    end)

    {:ok, db_path: db_path}
  end

  describe "compute_new_uris/2" do
    test "returns all URIs when database is empty", %{db_path: db_path} do
      sparql_uris = [
        "http://example.com/doc1",
        "http://example.com/doc2",
        "http://example.com/doc3"
      ]

      assert {:ok, new_uris} = XmlFileRepository.compute_new_uris(db_path, sparql_uris)
      assert length(new_uris) == 3
      assert Enum.sort(new_uris) == Enum.sort(sparql_uris)
    end

    test "returns empty list when all URIs exist", %{db_path: db_path} do
      # Insert a test file
      xml_content = TestHelper.sample_formex_xml()
      xml_path = TestHelper.create_test_xml_file(xml_content)
      uri = TestHelper.sample_cellar_uri()

      on_exit(fn -> TestHelper.cleanup_test_file(xml_path) end)

      path_uri_map = %{xml_path => uri}
      {:ok, _count} = XmlFileRepository.insert_files_with_uris(db_path, path_uri_map)

      # Query with the same URI
      assert {:ok, []} = XmlFileRepository.compute_new_uris(db_path, [uri])
    end

    test "returns only new URIs when some exist", %{db_path: db_path} do
      # Insert one file
      xml_content = TestHelper.sample_formex_xml()
      xml_path = TestHelper.create_test_xml_file(xml_content)
      existing_uri = "http://example.com/existing"

      on_exit(fn -> TestHelper.cleanup_test_file(xml_path) end)

      path_uri_map = %{xml_path => existing_uri}
      {:ok, _count} = XmlFileRepository.insert_files_with_uris(db_path, path_uri_map)

      # Query with mix of existing and new URIs
      sparql_uris = [
        existing_uri,
        "http://example.com/new1",
        "http://example.com/new2"
      ]

      assert {:ok, new_uris} = XmlFileRepository.compute_new_uris(db_path, sparql_uris)
      assert length(new_uris) == 2
      assert "http://example.com/new1" in new_uris
      assert "http://example.com/new2" in new_uris
      refute existing_uri in new_uris
    end

    test "handles large lists efficiently", %{db_path: db_path} do
      # Create 2000 URIs
      sparql_uris = Enum.map(1..2000, fn i -> "http://example.com/doc#{i}" end)

      assert {:ok, new_uris} = XmlFileRepository.compute_new_uris(db_path, sparql_uris)
      assert length(new_uris) == 2000
    end

    test "handles empty list", %{db_path: db_path} do
      assert {:ok, []} = XmlFileRepository.compute_new_uris(db_path, [])
    end
  end

  describe "insert_files_with_uris/3" do
    test "inserts a single file successfully", %{db_path: db_path} do
      xml_content = TestHelper.sample_formex_xml("ACT", "formex-05.56-20160701")
      xml_path = TestHelper.create_test_xml_file(xml_content)
      uri = TestHelper.sample_cellar_uri()

      on_exit(fn -> TestHelper.cleanup_test_file(xml_path) end)

      path_uri_map = %{xml_path => uri}
      assert {:ok, count} = XmlFileRepository.insert_files_with_uris(db_path, path_uri_map)
      assert count == 1

      # Verify the file was inserted
      assert {:ok, true} = XmlFileRepository.uri_exists?(db_path, uri)
    end

    test "inserts multiple files successfully", %{db_path: db_path} do
      # Create 3 test files with unique content
      files =
        Enum.map(1..3, fn i ->
          # Add unique content to each file so they get different UUIDs
          xml_content = String.replace(TestHelper.sample_formex_xml(), "Test Document", "Test Document #{i}")
          xml_path = TestHelper.create_test_xml_file(xml_content, filename: "test_#{i}.xml")
          uri = TestHelper.cellar_uri("16783d19-3ee7-4e8b-a2aa-e71d2ef8861#{i}", "0001", "03", "DOC_#{i}")
          {xml_path, uri}
        end)

      on_exit(fn ->
        Enum.each(files, fn {path, _uri} -> TestHelper.cleanup_test_file(path) end)
      end)

      path_uri_map = Map.new(files)
      assert {:ok, count} = XmlFileRepository.insert_files_with_uris(db_path, path_uri_map)
      assert count == 3

      # Verify all files were inserted
      Enum.each(files, fn {_path, uri} ->
        assert {:ok, true} = XmlFileRepository.uri_exists?(db_path, uri)
      end)
    end

    test "extracts XML metadata correctly", %{db_path: db_path} do
      xml_content = TestHelper.sample_formex_xml("DOC", "formex-06.00-20210715")
      xml_path = TestHelper.create_test_xml_file(xml_content)
      uri = TestHelper.sample_cellar_uri()

      on_exit(fn -> TestHelper.cleanup_test_file(xml_path) end)

      path_uri_map = %{xml_path => uri}
      assert {:ok, 1} = XmlFileRepository.insert_files_with_uris(db_path, path_uri_map)

      # Query the database to verify metadata
      {:ok, db} = Duckdbex.open(db_path)
      {:ok, conn} = Duckdbex.connection(db)
      {:ok, result} = Duckdbex.query(conn, "SELECT root_element, version FROM xml_files WHERE uri = $1", [uri])
      rows = Duckdbex.fetch_all(result)
      Duckdbex.release(result)
      Duckdbex.release(conn)
      Duckdbex.release(db)

      assert [[root_element, version]] = rows
      assert root_element == "DOC"
      assert version == "formex-06.00-20210715"
    end

    test "extracts cellar identifiers from URI", %{db_path: db_path} do
      xml_content = TestHelper.sample_formex_xml()
      xml_path = TestHelper.create_test_xml_file(xml_content)
      uri = TestHelper.cellar_uri("12345678-1234-1234-1234-123456789abc", "0042", "99", "ACT_1")

      on_exit(fn -> TestHelper.cleanup_test_file(xml_path) end)

      path_uri_map = %{xml_path => uri}
      assert {:ok, 1} = XmlFileRepository.insert_files_with_uris(db_path, path_uri_map)

      # Query the database to verify cellar IDs
      {:ok, db} = Duckdbex.open(db_path)
      {:ok, conn} = Duckdbex.connection(db)
      {:ok, result} = Duckdbex.query(conn, "SELECT work_id, expr_id, man_id, cs_id FROM xml_files WHERE uri = $1", [uri])
      rows = Duckdbex.fetch_all(result)
      Duckdbex.release(result)
      Duckdbex.release(conn)
      Duckdbex.release(db)

      assert [[work_id, expr_id, man_id, cs_id]] = rows
      assert work_id == "12345678-1234-1234-1234-123456789abc"
      assert expr_id == "0042"
      assert man_id == "99"
      assert cs_id == "ACT_1"
    end

    test "handles duplicate URIs with ON CONFLICT", %{db_path: db_path} do
      xml_content = TestHelper.sample_formex_xml()
      xml_path = TestHelper.create_test_xml_file(xml_content)
      uri = TestHelper.sample_cellar_uri()

      on_exit(fn -> TestHelper.cleanup_test_file(xml_path) end)

      path_uri_map = %{xml_path => uri}

      # Insert first time
      assert {:ok, 1} = XmlFileRepository.insert_files_with_uris(db_path, path_uri_map)

      # Insert second time - should not error, but count should be 0 due to ON CONFLICT DO NOTHING
      assert {:ok, 0} = XmlFileRepository.insert_files_with_uris(db_path, path_uri_map)

      # Verify only one record exists
      {:ok, db} = Duckdbex.open(db_path)
      {:ok, conn} = Duckdbex.connection(db)
      {:ok, result} = Duckdbex.query(conn, "SELECT COUNT(*) FROM xml_files WHERE uri = $1", [uri])
      [[count]] = Duckdbex.fetch_all(result)
      Duckdbex.release(result)
      Duckdbex.release(conn)
      Duckdbex.release(db)

      assert count == 1
    end

    test "handles empty map", %{db_path: db_path} do
      assert {:ok, 0} = XmlFileRepository.insert_files_with_uris(db_path, %{})
    end

    test "uses custom batch size", %{db_path: db_path} do
      # Create 5 test files with unique content
      files =
        Enum.map(1..5, fn i ->
          # Add unique content to each file so they get different UUIDs
          xml_content = String.replace(TestHelper.sample_formex_xml(), "Test Document", "Test Document #{i}")
          xml_path = TestHelper.create_test_xml_file(xml_content, filename: "batch_test_#{i}.xml")
          uri = TestHelper.cellar_uri("16783d19-3ee7-4e8b-a2aa-e71d2ef8861#{i}", "0001", "03", "DOC_#{i}")
          {xml_path, uri}
        end)

      on_exit(fn ->
        Enum.each(files, fn {path, _uri} -> TestHelper.cleanup_test_file(path) end)
      end)

      path_uri_map = Map.new(files)

      # Use batch size of 2
      assert {:ok, count} = XmlFileRepository.insert_files_with_uris(db_path, path_uri_map, batch_size: 2)
      assert count == 5
    end
  end

  describe "uri_exists?/2" do
    test "returns false when URI does not exist", %{db_path: db_path} do
      assert {:ok, false} = XmlFileRepository.uri_exists?(db_path, "http://example.com/nonexistent")
    end

    test "returns true when URI exists", %{db_path: db_path} do
      xml_content = TestHelper.sample_formex_xml()
      xml_path = TestHelper.create_test_xml_file(xml_content)
      uri = TestHelper.sample_cellar_uri()

      on_exit(fn -> TestHelper.cleanup_test_file(xml_path) end)

      path_uri_map = %{xml_path => uri}
      {:ok, _count} = XmlFileRepository.insert_files_with_uris(db_path, path_uri_map)

      assert {:ok, true} = XmlFileRepository.uri_exists?(db_path, uri)
    end
  end

  describe "get_all_uris/1" do
    test "returns empty list when database is empty", %{db_path: db_path} do
      assert {:ok, []} = XmlFileRepository.get_all_uris(db_path)
    end

    test "returns all URIs from database", %{db_path: db_path} do
      # Insert 3 files with unique content
      files =
        Enum.map(1..3, fn i ->
          # Add unique content to each file so they get different UUIDs
          xml_content = String.replace(TestHelper.sample_formex_xml(), "Test Document", "Test Document #{i}")
          xml_path = TestHelper.create_test_xml_file(xml_content, filename: "uri_test_#{i}.xml")
          uri = "http://example.com/doc#{i}"
          {xml_path, uri}
        end)

      on_exit(fn ->
        Enum.each(files, fn {path, _uri} -> TestHelper.cleanup_test_file(path) end)
      end)

      path_uri_map = Map.new(files)
      {:ok, _count} = XmlFileRepository.insert_files_with_uris(db_path, path_uri_map)

      assert {:ok, uris} = XmlFileRepository.get_all_uris(db_path)
      assert length(uris) == 3
      assert Enum.sort(uris) == ["http://example.com/doc1", "http://example.com/doc2", "http://example.com/doc3"]
    end
  end
end
