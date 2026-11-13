#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# Runs tbls on the schema the migrations make: SchemaDatabase builds it on Postgres in Docker, then
# runs tbls on it, in the workspace when bazel run runs this. Arguments: SchemaDatabase, the
# migrations' directory, tbls, its configuration, then tbls's command (lint; doc --rm-dist)
set -euo pipefail
schema_database=$1 migrations=$2 tbls=$3 config=$4
shift 4
exec "$schema_database" "$migrations" "$PWD/$tbls" "$@" --config "$PWD/$config"
