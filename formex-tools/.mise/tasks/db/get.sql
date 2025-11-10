{#- Jinja2 template for retrieving XML content by URI -#}
{#- Parameters:
  - uri: string - the URI of the file to retrieve (required)
-#}
select
    xml_content
from
    xml_files
where
    uri = '{{ uri }}'
order by
    id
limit 1;
