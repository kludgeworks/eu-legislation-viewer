#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
#MISE description="Scan dependencies for known vulnerabilities with OSV-Scanner"
#MISE quiet=true
# Bash 3.2+ only: that includes the Bash shipped with supported macOS versions.
set -euo pipefail

ui_prefix=lint:deps
# shellcheck source=tools/tasks/lib/ui.bash
source "$(dirname "${BASH_SOURCE[0]}")/../lib/ui.bash"

root=${REPO_ROOT:-$(cd "$(dirname "$0")/../../.." && pwd)}
cd "$root"

config_file="tools/build/osv-scanner.toml"
lock_file="maven_install.json"

if [[ ! -f "$lock_file" ]]; then
	die "Maven lockfile not found" path "$lock_file"
fi

tmp_dir=$(mktemp -d)
trap 'rm -rf "$tmp_dir"' EXIT

tmp_pom="$tmp_dir/pom.xml"

# Generate minimal pom.xml from maven_install.json artifacts to allow OSV-Scanner to scan Maven dependencies
python3 -c "
import json, sys

with open('$lock_file') as f:
    data = json.load(f)

deps = []
for coord, details in data.get('artifacts', {}).items():
    parts = coord.split(':')
    if len(parts) >= 2:
        g, a = parts[0], parts[1]
        v = details.get('version')
        if v:
            deps.append(f'''    <dependency>
      <groupId>{g}</groupId>
      <artifactId>{a}</artifactId>
      <version>{v}</version>
    </dependency>''')

pom = f'''<project xmlns=\"http://maven.apache.org/POM/4.0.0\">
  <modelVersion>4.0.0</modelVersion>
  <groupId>kludgeworks</groupId>
  <artifactId>eu-legislation-viewer</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <dependencies>
{chr(10).join(deps)}
  </dependencies>
</project>'''

with open('$tmp_pom', 'w') as out:
    out.write(pom)
"

args=(scan source --no-resolve -r .)
if [[ -f "$config_file" ]]; then
	args+=(--config "$config_file")
fi
args+=(-L "$tmp_pom")

info "Running OSV-Scanner"
osv-scanner "${args[@]}"
