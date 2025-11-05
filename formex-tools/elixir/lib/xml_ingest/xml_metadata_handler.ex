defmodule XmlIngest.XmlMetadataHandler do
  @moduledoc """
  Saxy handler for extracting XML metadata from the root element.
  Only processes the first element and then halts parsing.
  """

  @behaviour Saxy.Handler

  @impl true
  def handle_event(:start_document, _prolog, _state) do
    {:ok, %{}}
  end

  @impl true
  def handle_event(:start_element, {name, attributes}, _state) do
    # Found root element - extract metadata and stop parsing
    root_element = to_string(name)

    # Look for schema location attribute (with or without prefix)
    version =
      Enum.find_value(attributes, fn
        {"xsi:noNamespaceSchemaLocation", value} -> extract_version(value)
        {"noNamespaceSchemaLocation", value} -> extract_version(value)
        _ -> nil
      end)

    result = %{root_element: root_element, version: version}

    # Halt parsing - we have what we need
    {:halt, result}
  end

  @impl true
  def handle_event(_event, _data, state) do
    {:ok, state}
  end

  # Extract version string from schema location attribute
  defp extract_version(nil), do: nil
  defp extract_version(""), do: nil

  defp extract_version(value) do
    case String.split(value, "/") do
      [] -> nil
      parts -> List.last(parts)
    end
  end
end
