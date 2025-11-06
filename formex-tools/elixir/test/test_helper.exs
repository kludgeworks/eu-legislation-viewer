defmodule FormexV4.TestHelper do
  @moduledoc """
  Test helper utilities for setting up test databases and fixtures.
  """

  @doc """
  Creates a temporary test database with the formex_documents schema.
  Returns the path to the database file.
  Database is created in the system temp directory.

  """
  def setup_test_db do
    db_path = generate_temp_db_path()
    {:ok, db} = Duckdbex.open(db_path)
    {:ok, conn} = Duckdbex.connection(db)

    # Create formex_documents table with same schema as production
    sql = """
    CREATE TABLE IF NOT EXISTS formex_documents (
        id UUID PRIMARY KEY,
        xml_content VARCHAR,
        version VARCHAR,
        root_element VARCHAR,
        file_size BIGINT,
        uri VARCHAR,
        work_id UUID,
        expr_id VARCHAR,
        man_id VARCHAR,
        cs_id VARCHAR
    );
    """

    {:ok, _} = Duckdbex.query(conn, sql)
    Duckdbex.release(conn)
    Duckdbex.release(db)

    db_path
  end

  @doc """
  Removes a test database file.
  """
  def teardown_test_db(db_path) do
    if File.exists?(db_path) do
      File.rm!(db_path)
    end

    :ok
  end

  @doc """
  Creates a test XML file with the given content.
  Returns the path to the created file.
  """
  def create_test_xml_file(content, opts \\ []) do
    filename = Keyword.get(opts, :filename, "test_#{:rand.uniform(100_000)}.xml")
    dir = System.tmp_dir!()
    path = Path.join(dir, filename)

    File.write!(path, content)
    path
  end

  @doc """
  Removes a test XML file.
  """
  def cleanup_test_file(path) do
    if File.exists?(path) do
      File.rm!(path)
    end

    :ok
  end

  @doc """
  Generates a sample Formex XML document with the given root element and version.
  """
  def sample_formex_xml(root_element \\ "ACT", version \\ "formex-05.56-20160701") do
    """
    <?xml version="1.0" encoding="UTF-8"?>
    <#{root_element} xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:noNamespaceSchemaLocation="#{version}">
      <METADATA>
        <IDENTIFIER>
          <FMX.NOTICE>32016L0943</FMX.NOTICE>
        </IDENTIFIER>
      </METADATA>
      <CONTENT>
        <TITLE>Test Document</TITLE>
        <TEXT>This is a test document.</TEXT>
      </CONTENT>
    </#{root_element}>
    """
  end

  @doc """
  Generates a valid Europa Publications URI for testing.
  """
  def sample_cellar_uri do
    "http://publications.europa.eu/resource/cellar/16783d19-3ee7-4e8b-a2aa-e71d2ef88616.0001.03/DOC_1"
  end

  @doc """
  Generates a cellar URI with specific components for testing.
  """
  def cellar_uri(work_id, expr_id, man_id, cs_id) do
    "http://publications.europa.eu/resource/cellar/#{work_id}.#{expr_id}.#{man_id}/#{cs_id}"
  end

  # Private helpers

  defp generate_temp_db_path do
    timestamp = System.system_time(:millisecond)
    random = :rand.uniform(100_000)
    filename = "test_#{timestamp}_#{random}.duckdb"
    Path.join(System.tmp_dir!(), filename)
  end
end

# Start ExUnit with integration tests excluded by default
# Run with: mix test --include integration
ExUnit.start(exclude: [:integration])
