-- Install and load the webbed extension for XML support
INSTALL webbed FROM community; -- noqa
LOAD webbed; -- noqa

-- Main table for storing XML files with metadata
CREATE TABLE IF NOT EXISTS xml_files (
    id UUID PRIMARY KEY,
    xml_content XML,
    version VARCHAR,
    root_element VARCHAR,
    file_size BIGINT,
    uri VARCHAR,
    work_id UUID,
    expr_id VARCHAR,
    man_id VARCHAR,
    cs_id VARCHAR
);

-- Index on version for quick version lookups
CREATE INDEX IF NOT EXISTS idx_xml_files_version ON xml_files(version);

-- Index on root_element for filtering by element type
CREATE INDEX IF NOT EXISTS idx_xml_files_root_element ON xml_files(root_element);

-- Indexes on cellar identifiers for filtering and joining
CREATE INDEX IF NOT EXISTS idx_xml_files_work_id ON xml_files(work_id);
CREATE INDEX IF NOT EXISTS idx_xml_files_expr_id ON xml_files(expr_id);
CREATE INDEX IF NOT EXISTS idx_xml_files_man_id ON xml_files(man_id);
CREATE INDEX IF NOT EXISTS idx_xml_files_cs_id ON xml_files(cs_id);

-- Index on URI for direct lookups
CREATE INDEX IF NOT EXISTS idx_xml_files_uri ON xml_files(uri);
