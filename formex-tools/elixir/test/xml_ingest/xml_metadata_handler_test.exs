defmodule XmlIngest.XmlMetadataHandlerTest do
  use ExUnit.Case, async: true

  alias XmlIngest.XmlMetadataHandler

  describe "handle_event/3" do
    test "extracts root element name" do
      xml = """
      <?xml version="1.0"?>
      <ACT>
        <CONTENT>Test</CONTENT>
      </ACT>
      """

      state = %{root_element: nil, version: nil}
      {:halt, result, _remaining} = Saxy.parse_string(xml, XmlMetadataHandler, state)

      assert result.root_element == "ACT"
    end

    test "extracts version from xsi:noNamespaceSchemaLocation" do
      xml = """
      <?xml version="1.0"?>
      <DOC xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           xsi:noNamespaceSchemaLocation="formex-05.56-20160701">
        <CONTENT>Test</CONTENT>
      </DOC>
      """

      state = %{root_element: nil, version: nil}
      {:halt, result, _remaining} = Saxy.parse_string(xml, XmlMetadataHandler, state)

      assert result.root_element == "DOC"
      assert result.version == "formex-05.56-20160701"
    end

    test "extracts version from noNamespaceSchemaLocation without prefix" do
      xml = """
      <?xml version="1.0"?>
      <ANNEX noNamespaceSchemaLocation="formex-06.00-20210715">
        <CONTENT>Test</CONTENT>
      </ANNEX>
      """

      state = %{root_element: nil, version: nil}
      {:halt, result, _remaining} = Saxy.parse_string(xml, XmlMetadataHandler, state)

      assert result.root_element == "ANNEX"
      assert result.version == "formex-06.00-20210715"
    end

    test "extracts version from schema location with path" do
      xml = """
      <?xml version="1.0"?>
      <ACT xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           xsi:noNamespaceSchemaLocation="http://example.com/schema/formex-05.56-20160701">
        <CONTENT>Test</CONTENT>
      </ACT>
      """

      state = %{root_element: nil, version: nil}
      {:halt, result, _remaining} = Saxy.parse_string(xml, XmlMetadataHandler, state)

      assert result.root_element == "ACT"
      assert result.version == "formex-05.56-20160701"
    end

    test "handles missing schema location attribute" do
      xml = """
      <?xml version="1.0"?>
      <ACT>
        <CONTENT>Test</CONTENT>
      </ACT>
      """

      state = %{root_element: nil, version: nil}
      {:halt, result, _remaining} = Saxy.parse_string(xml, XmlMetadataHandler, state)

      assert result.root_element == "ACT"
      assert result.version == nil
    end

    test "stops parsing after first element" do
      xml = """
      <?xml version="1.0"?>
      <ACT xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           xsi:noNamespaceSchemaLocation="formex-05.56-20160701">
        <CONTENT>
          <NESTED>This should not be parsed</NESTED>
        </CONTENT>
      </ACT>
      """

      state = %{root_element: nil, version: nil}
      {:halt, result, _remaining} = Saxy.parse_string(xml, XmlMetadataHandler, state)

      # Should only extract root element metadata
      assert result.root_element == "ACT"
      assert result.version == "formex-05.56-20160701"
    end
  end
end
