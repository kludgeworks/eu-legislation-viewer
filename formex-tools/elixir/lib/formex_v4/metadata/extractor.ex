defmodule FormexV4.Metadata.Extractor do
  @moduledoc """
  Extracts metadata from XML files for database ingestion.

  Implements strict validation - all metadata fields must be present.
  Returns {:error, reason} if any required field is missing or invalid.
  """

  require Logger

  # Pattern to extract cellar identifier components from URI
  # Format: {work-id}.{expr-id}.{man-id}/{cs-id}
  @cellar_pattern ~r/([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})\.(\d{4})\.(\d{2})\/([A-Z0-9_]+)/i

  # Namespace for UUID v5 generation
  @namespace_value "publications.europa.eu/resource/cellar"

  @type metadata :: %{
          id: String.t(),
          file_path: String.t(),
          uri: String.t(),
          root_element: String.t(),
          version: String.t(),
          file_size: integer(),
          work_id: String.t(),
          expr_id: String.t(),
          man_id: String.t(),
          cs_id: String.t()
        }

  @doc """
  Extracts metadata from an XML file with strict validation.

  All fields are required - returns {:error, reason} if any field is missing.

  ## Parameters
    - file_path: Path to the XML file
    - uri: Cellar URI for the resource

  ## Returns
    - {:ok, metadata_map} on success with all required fields
    - {:error, reason} if validation fails or any required field is missing

  ## Examples

      iex> extract("/path/to/file.xml", "http://publications.europa.eu/resource/cellar/abc.0001.01/DOC_1")
      {:ok, %{
        id: "...",
        file_path: "/path/to/file.xml",
        uri: "http://...",
        root_element: "ACT",
        version: "formex-05.59-20170418.xd",
        file_size: 12345,
        work_id: "abc...",
        expr_id: "0001",
        man_id: "01",
        cs_id: "DOC_1"
      }}

      iex> extract("/path/missing-version.xml", "http://...")
      {:error, "XML missing required version attribute"}

  """
  @spec extract(String.t(), String.t()) :: {:ok, metadata()} | {:error, String.t()}
  def extract(file_path, uri) do
    with {:ok, file_size} <- get_file_size(file_path),
         {:ok, xml_metadata} <- parse_xml_metadata(file_path),
         {:ok, cellar_ids} <- extract_cellar_ids(uri),
         {:ok, content_id} <- generate_content_id(file_path) do
      metadata = %{
        id: content_id,
        file_path: file_path,
        uri: uri,
        root_element: xml_metadata.root_element,
        version: xml_metadata.version,
        file_size: file_size,
        work_id: cellar_ids.work_id,
        expr_id: cellar_ids.expr_id,
        man_id: cellar_ids.man_id,
        cs_id: cellar_ids.cs_id
      }

      {:ok, metadata}
    end
  end

  # ==================== Private Helper Functions ====================

  defp get_file_size(path) do
    case File.stat(path) do
      {:ok, %{size: size}} ->
        {:ok, size}

      {:error, reason} ->
        {:error, "Failed to read file stats: #{inspect(reason)}"}
    end
  end

  defp parse_xml_metadata(path) do
    try do
      # Read first chunk of file to parse root element
      case File.open(path, [:read]) do
        {:ok, file} ->
          chunk = IO.read(file, 8192)
          File.close(file)

          case Saxy.parse_string(chunk, FormexV4.Metadata.Handler, %{}) do
            {:ok, result} ->
              validate_xml_metadata(result)

            {:error, reason} ->
              {:error, "Failed to parse XML: #{inspect(reason)}"}
          end

        {:error, reason} ->
          {:error, "Failed to open file: #{inspect(reason)}"}
      end
    rescue
      e ->
        {:error, "XML parsing error: #{Exception.message(e)}"}
    end
  end

  defp validate_xml_metadata(%{root_element: root, version: version})
       when is_binary(root) and root != "" and root != "unknown" and is_binary(version) and
              version != "" do
    {:ok, %{root_element: root, version: version}}
  end

  defp validate_xml_metadata(%{version: nil}) do
    {:error, "XML missing required version attribute (xsi:noNamespaceSchemaLocation)"}
  end

  defp validate_xml_metadata(%{version: ""}) do
    {:error, "XML version attribute is empty"}
  end

  defp validate_xml_metadata(%{root_element: root}) when root in ["", "unknown"] do
    {:error, "Failed to extract valid root element from XML"}
  end

  defp validate_xml_metadata(_) do
    {:error, "Invalid XML metadata structure"}
  end

  defp extract_cellar_ids(uri) do
    case Regex.run(@cellar_pattern, uri) do
      [_, work_id, expr_id, man_id, cs_id] ->
        {:ok,
         %{
           work_id: work_id,
           expr_id: expr_id,
           man_id: man_id,
           cs_id: cs_id
         }}

      nil ->
        {:error, "URI does not match expected cellar pattern: #{uri}"}
    end
  end

  defp generate_content_id(path) do
    try do
      content_hash = hash_file_content(path)
      # Generate UUID v5 using the uuid library
      uuid = UUID.uuid5(:url, @namespace_value <> "/" <> content_hash)
      {:ok, uuid}
    rescue
      e ->
        {:error, "Failed to generate content ID: #{Exception.message(e)}"}
    end
  end

  defp hash_file_content(path) do
    File.stream!(path, [], 2048)
    |> Enum.reduce(:crypto.hash_init(:sha256), fn chunk, acc ->
      :crypto.hash_update(acc, chunk)
    end)
    |> :crypto.hash_final()
    |> Base.encode16(case: :lower)
  end
end
