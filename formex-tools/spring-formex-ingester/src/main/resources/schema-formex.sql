-- Application Schema for SQLite
-- Application-specific tables defined below

-- Main table for storing Formex documents with metadata and file references
-- XML content is stored in the filesystem, this table stores metadata and file paths
CREATE TABLE IF NOT EXISTS formex_documents (
  id TEXT PRIMARY KEY,
  file_path TEXT NOT NULL,
  version TEXT,
  root_element TEXT,
  file_size INTEGER,
  uri TEXT UNIQUE,
  work_id TEXT NOT NULL,
  expr_id TEXT NOT NULL,
  man_id TEXT NOT NULL,
  cs_id TEXT NOT NULL
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
