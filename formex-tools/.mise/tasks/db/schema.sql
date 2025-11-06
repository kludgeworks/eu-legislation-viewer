-- Install and load the webbed extension for XML support
INSTALL webbed FROM community; -- noqa
LOAD webbed; -- noqa

-- Main table for storing Formex documents with metadata
CREATE TABLE IF NOT EXISTS formex_documents (
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
CREATE INDEX IF NOT EXISTS idx_formex_documents_version ON formex_documents(version);

-- Index on root_element for filtering by element type
CREATE INDEX IF NOT EXISTS idx_formex_documents_root_element ON formex_documents(root_element);

-- Indexes on cellar identifiers for filtering and joining
CREATE INDEX IF NOT EXISTS idx_formex_documents_work_id ON formex_documents(work_id);
CREATE INDEX IF NOT EXISTS idx_formex_documents_expr_id ON formex_documents(expr_id);
CREATE INDEX IF NOT EXISTS idx_formex_documents_man_id ON formex_documents(man_id);
CREATE INDEX IF NOT EXISTS idx_formex_documents_cs_id ON formex_documents(cs_id);

-- Index on URI for direct lookups
CREATE INDEX IF NOT EXISTS idx_formex_documents_uri ON formex_documents(uri);
