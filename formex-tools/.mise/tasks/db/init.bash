#!/usr/bin/env bash
#MISE description="Initialize DuckDB schema for XML file storage and analysis"
#MISE confirm="Are you sure you want to initialize the database? This will overwrite any existing data."
#USAGE arg "<db-name>" help="Database name (will create .db file)" default="formex.db"

duckdb "$usage_db_name" < .mise/tasks/db/schema.sql