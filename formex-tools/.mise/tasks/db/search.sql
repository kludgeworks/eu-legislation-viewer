{#- Jinja2 template for searching XML files in DuckDB -#}
{#- Parameters:
  - versions: list of version strings (required)
  - root_elements: list of root element filters (optional)
  - with_version: include version and root_element in output (default: false)
  - limit: maximum number of results (optional)
-#}
select
    {%- if with_version %}
        uri,
        version,
        root_element
    {%- else %}
        uri
    {%- endif %}
from
    xml_files
where
    1 = 1
    {%- if versions %}
        and (
            {%- for version in versions %}
                version = '{{ version }}'
                {%- if not loop.last %} or {%- endif %}
            {%- endfor %}
        )
    {%- endif %}
    {%- if root_elements %}
        and (
            {%- for element in root_elements %}
                root_element = '{{ element }}'
                {%- if not loop.last %} or {%- endif %}
            {%- endfor %}
        )
    {%- endif %}
{%- if limit %}
    limit {{ limit }}
{%- endif %};
