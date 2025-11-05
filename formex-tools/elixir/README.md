# FormexV4 - Elixir Implementation

Sync Formex v4 XML files from Europa Publications SPARQL endpoint into DuckDB.

## Configuration

Configuration is done via environment variables:

### Required

- `FORMEX_DB_PATH` - Path to DuckDB database file (default: `formex.db`)
- `SPARQL_ENDPOINT` - SPARQL endpoint URL (default: `https://publications.europa.eu/webapi/rdf/sparql`)

### Optional

- `CACHE_DIR` - File cache directory (default: `.cache/formex-v4`)
- `DOWNLOAD_CONCURRENCY` - Number of parallel downloads (default: `12`)
- `BATCH_SIZE` - Database batch insert size (default: `500`)
- `SPARQL_LIMIT` - Limit SPARQL results for testing (default: no limit)

## Usage

```bash
# Install dependencies
mix deps.get

# Run the sync
mix formex.sync

# Run with custom settings
FORMEX_DB_PATH=custom.db DOWNLOAD_CONCURRENCY=20 mix formex.sync

# Lint the code
mix credo
```

## Requirements

- Elixir ~> 1.15
- DuckDB database with `xml_files` table (see `../db/schema.sql`)

## Dependencies

- `broadway` - Data processing pipeline
- `req` - HTTP client
- `duckdbex` - DuckDB driver
- `saxy` - Streaming XML parser
- `jason` - JSON parser
- `credo` - Linting (dev/test only)
