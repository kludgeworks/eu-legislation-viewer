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

      metadata = %{
        id: UUID.uuid1(),
        file_path: xml_path,
        uri: uri,
        root_element: "ACT",
        version: "formex-05.56-20160701",
        file_size: File.stat!(xml_path).size,
        work_id: UUID.uuid1(),
        expr_id: "0001",
        man_id: "03",
        cs_id: "DOC_1"
      }

      {:ok, _count} = XmlFileRepository.insert_metadata_batch(db_path, [metadata])

      # Query with the same URI
      assert {:ok, []} = XmlFileRepository.compute_new_uris(db_path, [uri])
    end

    test "returns only new URIs when some exist", %{db_path: db_path} do
      # Insert one file
      xml_content = TestHelper.sample_formex_xml()
      xml_path = TestHelper.create_test_xml_file(xml_content)
      existing_uri = "http://example.com/existing"

      on_exit(fn -> TestHelper.cleanup_test_file(xml_path) end)

      metadata = %{
        id: UUID.uuid1(),
        file_path: xml_path,
        uri: existing_uri,
        root_element: "ACT",
        version: "formex-05.56-20160701",
        file_size: File.stat!(xml_path).size,
        work_id: UUID.uuid1(),
        expr_id: "0001",
        man_id: "03",
        cs_id: "DOC_1"
      }

      {:ok, _count} = XmlFileRepository.insert_metadata_batch(db_path, [metadata])

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

  describe "insert_metadata_batch/3" do
    test "inserts a single metadata record successfully", %{db_path: db_path} do
      xml_content = TestHelper.sample_formex_xml("ACT", "formex-05.56-20160701")
      xml_path = TestHelper.create_test_xml_file(xml_content)
      uri = TestHelper.sample_cellar_uri()

      on_exit(fn -> TestHelper.cleanup_test_file(xml_path) end)

      # Create metadata map
      metadata = %{
        id: UUID.uuid1(),
        file_path: xml_path,
        uri: uri,
        root_element: "ACT",
        version: "formex-05.56-20160701",
        file_size: File.stat!(xml_path).size,
        work_id: UUID.uuid1(),
        expr_id: "0001",
        man_id: "03",
        cs_id: "DOC_1"
      }

      assert {:ok, count} = XmlFileRepository.insert_metadata_batch(db_path, [metadata])
      assert count == 1

      # Verify the file was inserted
      {:ok, db} = Duckdbex.open(db_path)
      {:ok, conn} = Duckdbex.connection(db)
      {:ok, result} = Duckdbex.query(conn, "SELECT COUNT(*) FROM xml_files WHERE uri = $1", [uri])
      [[uri_count]] = Duckdbex.fetch_all(result)
      Duckdbex.release(result)
      Duckdbex.release(conn)
      Duckdbex.release(db)

      assert uri_count == 1
    end

    test "inserts multiple metadata records successfully", %{db_path: db_path} do
      # Create 3 test files with unique content
      files_with_metadata =
        Enum.map(1..3, fn i ->
          xml_content =
            String.replace(TestHelper.sample_formex_xml(), "Test Document", "Test Document #{i}")

          xml_path = TestHelper.create_test_xml_file(xml_content, filename: "test_#{i}.xml")

          work_id = UUID.uuid1()

          metadata = %{
            id: UUID.uuid1(),
            file_path: xml_path,
            uri:
              TestHelper.cellar_uri(
                work_id,
                "0001",
                "03",
                "DOC_#{i}"
              ),
            root_element: "ACT",
            version: "formex-05.56-20160701",
            file_size: File.stat!(xml_path).size,
            work_id: work_id,
            expr_id: "0001",
            man_id: "03",
            cs_id: "DOC_#{i}"
          }

          {xml_path, metadata}
        end)

      on_exit(fn ->
        Enum.each(files_with_metadata, fn {path, _metadata} ->
          TestHelper.cleanup_test_file(path)
        end)
      end)

      metadata_list = Enum.map(files_with_metadata, fn {_path, metadata} -> metadata end)
      assert {:ok, count} = XmlFileRepository.insert_metadata_batch(db_path, metadata_list)
      assert count == 3

      # Verify all files were inserted
      {:ok, db} = Duckdbex.open(db_path)
      {:ok, conn} = Duckdbex.connection(db)

      Enum.each(metadata_list, fn metadata ->
        {:ok, result} =
          Duckdbex.query(conn, "SELECT COUNT(*) FROM xml_files WHERE uri = $1", [metadata.uri])

        [[uri_count]] = Duckdbex.fetch_all(result)
        Duckdbex.release(result)
        assert uri_count == 1
      end)

      Duckdbex.release(conn)
      Duckdbex.release(db)
    end

    test "reads XML content using DuckDB read_text", %{db_path: db_path} do
      xml_content = TestHelper.sample_formex_xml("DOC", "formex-06.00-20210715")
      xml_path = TestHelper.create_test_xml_file(xml_content)
      uri = TestHelper.sample_cellar_uri()

      on_exit(fn -> TestHelper.cleanup_test_file(xml_path) end)

      metadata = %{
        id: UUID.uuid1(),
        file_path: xml_path,
        uri: uri,
        root_element: "DOC",
        version: "formex-06.00-20210715",
        file_size: File.stat!(xml_path).size,
        work_id: UUID.uuid1(),
        expr_id: "0001",
        man_id: "03",
        cs_id: "DOC_1"
      }

      assert {:ok, 1} = XmlFileRepository.insert_metadata_batch(db_path, [metadata])

      # Query the database to verify content was stored
      {:ok, db} = Duckdbex.open(db_path)
      {:ok, conn} = Duckdbex.connection(db)

      {:ok, result} =
        Duckdbex.query(conn, "SELECT xml_content FROM xml_files WHERE uri = $1", [uri])

      rows = Duckdbex.fetch_all(result)
      Duckdbex.release(result)
      Duckdbex.release(conn)
      Duckdbex.release(db)

      assert [[stored_content]] = rows
      assert String.contains?(stored_content, "<DOC")
      assert String.contains?(stored_content, "formex-06.00-20210715")
    end

    test "handles duplicate IDs with ON CONFLICT", %{db_path: db_path} do
      xml_content = TestHelper.sample_formex_xml()
      xml_path = TestHelper.create_test_xml_file(xml_content)
      uri = TestHelper.sample_cellar_uri()

      on_exit(fn -> TestHelper.cleanup_test_file(xml_path) end)

      metadata = %{
        id: UUID.uuid1(),
        file_path: xml_path,
        uri: uri,
        root_element: "ACT",
        version: "formex-05.56-20160701",
        file_size: File.stat!(xml_path).size,
        work_id: UUID.uuid1(),
        expr_id: "0001",
        man_id: "03",
        cs_id: "DOC_1"
      }

      # Insert first time
      assert {:ok, 1} = XmlFileRepository.insert_metadata_batch(db_path, [metadata])

      # Insert second time - should not error, but count should be 0 due to ON CONFLICT DO NOTHING
      assert {:ok, 0} = XmlFileRepository.insert_metadata_batch(db_path, [metadata])

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

    test "handles empty list", %{db_path: db_path} do
      assert {:ok, 0} = XmlFileRepository.insert_metadata_batch(db_path, [])
    end

    test "uses custom batch size", %{db_path: db_path} do
      # Create 5 test files with unique content
      files_with_metadata =
        Enum.map(1..5, fn i ->
          xml_content =
            String.replace(TestHelper.sample_formex_xml(), "Test Document", "Test Document #{i}")

          xml_path = TestHelper.create_test_xml_file(xml_content, filename: "batch_test_#{i}.xml")

          work_id = UUID.uuid1()

          metadata = %{
            id: UUID.uuid1(),
            file_path: xml_path,
            uri:
              TestHelper.cellar_uri(
                work_id,
                "0001",
                "03",
                "DOC_#{i}"
              ),
            root_element: "ACT",
            version: "formex-05.56-20160701",
            file_size: File.stat!(xml_path).size,
            work_id: work_id,
            expr_id: "0001",
            man_id: "03",
            cs_id: "DOC_#{i}"
          }

          {xml_path, metadata}
        end)

      on_exit(fn ->
        Enum.each(files_with_metadata, fn {path, _metadata} ->
          TestHelper.cleanup_test_file(path)
        end)
      end)

      metadata_list = Enum.map(files_with_metadata, fn {_path, metadata} -> metadata end)

      # Use batch size of 2
      assert {:ok, count} =
               XmlFileRepository.insert_metadata_batch(db_path, metadata_list, batch_size: 2)

      assert count == 5
    end
  end
end
