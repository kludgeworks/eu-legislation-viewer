#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
#MISE description="Mirror one language's Formex corpus from Cellar for the regression test (hours; resumable)"
# EUR-Lex's 24 languages, by their EU authority codes: every language Cellar has Formex in
#USAGE flag "--language <code>" default="ENG" help="An EU authority language code" { choices "BUL" "CES" "DAN" "DEU" "ELL" "ENG" "EST" "FIN" "FRA" "GLE" "HRV" "HUN" "ITA" "LAV" "LIT" "MLT" "NLD" "POL" "POR" "RON" "SLK" "SLV" "SPA" "SWE" }
#USAGE flag "--slices <list>" default="0,1,2,3,4,5,6,7,8,9,a,b,c,d,e,f" help="Cellar UUID prefixes"
#USAGE flag "--parallel <n>" default="5" help="Downloads at a time"
#
# Mirrors Cellar's Formex 4 items in one language, for the corpus regression test (CorpusTests):
#
#   mise run corpus:fetch [--language ENG] [--slices 0,1,…,f] [--parallel 5]
#
# formex-corpus/{language}/ at the repository root (ignored by git and Bazel) then holds:
#   manifest.tsv                 the complete manifestations: Cellar ID, lastModified, CELEX
#   files/{manifestation}/       each one's files; a zip-only manifestation's unpacked
#   zips/{manifestation}/        the zips as fetched
#   index/{slice}.tsv            the query's answer (formex-items.rq), slice by slice
#   failures.tsv, mismatches.tsv what the last run couldn't fetch, and SHA-1s that didn't match
#
# Run it again to bring the mirror up to date: a manifestation whose lastModified hasn't changed and
# whose files are all present costs no request; one that has changed is fetched afresh, and one Cellar
# no longer lists is removed. Failures are fetched again on the next run.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
endpoint=https://publications.europa.eu/webapi/rdf/sparql
agent="kludgeworks-viewer/0.0 (+https://kludgeworks.groknull.dev)"
language=${usage_language:?}
slices=${usage_slices:?}
slices=${slices//,/ }
parallel=${usage_parallel:?}

invalid() {
	echo "corpus:fetch: $1" >&2
	exit 2
}
[[ $parallel =~ ^[1-9][0-9]?$ ]] || invalid "not a number of downloads: $parallel"
for slice in $slices; do
	[[ $slice =~ ^[0-9a-f]{1,8}$ ]] || invalid "not a slice: $slice"
done

dir=$root/formex-corpus/$(echo "$language" | tr '[:upper:]' '[:lower:]')
mkdir -p "$dir/index" "$dir/files" "$dir/zips"
dir=$(cd "$dir" && pwd)
touch "$dir/manifest.tsv"
: >"$dir/failures.tsv"
: >"$dir/mismatches.tsv"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# The slice's items, as SPARQL TSV: every value a quoted string, or empty where unbound.
query() {
	sed -e "s/{{language}}/$language/" -e "s/{{prefix}}/$1/" "$here/formex-items.rq" |
		curl -sS --fail --retry 5 --retry-all-errors --retry-delay 30 --max-time 1200 \
			-A "$agent" -H 'Accept: text/tab-separated-values' \
			--data-urlencode query@- "$endpoint" >"$dir/index/$1.tsv.part"
	mv "$dir/index/$1.tsv.part" "$dir/index/$1.tsv"
}

# What to have for each item, from the index and the previous manifest. Lines of
#   item  {manifestation} {lastModified} {CELEX} {changed: 1|0} {item URI} {path} {SHA-1}
#   gone  {manifestation}
# An empty CELEX or SHA-1 is "-", since read collapses empty fields. A zip beside a manifestation's
# own files bundles them, and is left out; a name that isn't a plain file name is replaced by the last
# segment of the item's URI.
plan() {
	awk -F'\t' -v OFS='\t' -v slice="$1" '
		function unquote(s) { sub(/^"/, "", s); sub(/"$/, "", s); return s }
		function last(uri) { sub(/.*\//, "", uri); return uri }
		function dash(s) { return s == "" ? "-" : s }
		FILENAME == ARGV[1] { old[$1] = $2; next }
		FNR == 1 { next }
		{
			for (f = 1; f <= NF; f++) $f = unquote($f)
			id = last($1)
			if (id !~ /^[0-9a-f-]+(\.[0-9]+)+$/) next
			n++; mid[n] = id; uri[n] = $2; zip[n] = ($3 ~ /^application\/zip/)
			name[n] = ($4 ~ /^[A-Za-z0-9][A-Za-z0-9._-]*$/) ? $4 : last($2)
			sha[n] = dash(tolower($5)); modified[id] = $6; celex[id] = dash($7); listed[id] = 1
			if (!zip[n]) files[id] = 1
		}
		END {
			for (k = 1; k <= n; k++) {
				id = mid[k]
				if (zip[k] && files[id]) continue
				changed = (id in old) && old[id] != modified[id]
				print "item", id, modified[id], celex[id], changed, uri[k], \
					(zip[k] ? "zips/" : "files/") id "/" name[k], sha[k]
			}
			for (id in old) if (index(id, slice) == 1 && !(id in listed)) print "gone", id
		}' "$dir/manifest.tsv" "$dir/index/$1.tsv"
}

for slice in $slices; do
	echo "slice $slice: querying" >&2
	query "$slice"
	plan "$slice" >"$work/plan"

	# a changed or withdrawn manifestation is removed, and a changed one fetched afresh
	awk -F'\t' '$1 == "gone" || $5 == 1 { print $2 }' "$work/plan" | sort -u >"$work/stale"
	while read -r id; do rm -rf "${dir:?}/files/$id" "${dir:?}/zips/$id"; done <"$work/stale"

	# a curl config for each item not yet on disk
	: >"$work/curl"
	: >"$work/fetched"
	while IFS=$'\t' read -r kind _ _ _ _ uri path sha1; do
		[ "$kind" = item ] && [ ! -e "$dir/$path" ] || continue
		printf 'url = "%s"\noutput = "%s"\n' "$uri" "$path" >>"$work/curl"
		printf '%s\t%s\n' "$path" "$sha1" >>"$work/fetched"
	done <"$work/plan"
	echo "slice $slice: fetching $(wc -l <"$work/fetched" | tr -d ' ') files" >&2
	if [ -s "$work/curl" ]; then
		(cd "$dir" && curl --parallel --parallel-max "$parallel" --config "$work/curl" \
			--create-dirs --location --fail --remove-on-error --retry 5 --retry-all-errors \
			--retry-delay 10 -sS -A "$agent") || true
	fi

	# unpack each zip whose manifestation has no files yet
	for zip in "$dir/zips/$slice"*/*; do
		[ -e "$zip" ] || continue
		id=$(basename "$(dirname "$zip")")
		if [ -z "$(ls -A "$dir/files/$id" 2>/dev/null)" ]; then
			mkdir -p "$dir/files/$id"
			# -j keeps entries in the folder, whatever paths the zip holds
			unzip -qq -o -j "$zip" -d "$dir/files/$id" || rm -rf "${dir:?}/files/$id"
		fi
	done

	# the SHA-1s Cellar records, against the bytes fetched
	awk -F'\t' '$2 != "-" { print $2 "  " $1 }' "$work/fetched" >"$work/sums"
	if [ -s "$work/sums" ]; then
		(cd "$dir" && shasum -a 1 -c "$work/sums" 2>/dev/null || true) |
			awk '/: FAILED$/ { sub(/: FAILED$/, ""); print }' >>"$dir/mismatches.tsv"
	fi

	# complete: every item on disk, and a zip unpacked
	: >"$work/incomplete"
	while IFS=$'\t' read -r kind id _ _ _ uri path _; do
		[ "$kind" = item ] || continue
		if [ ! -e "$dir/$path" ] || [ -z "$(ls -A "$dir/files/$id" 2>/dev/null)" ]; then
			echo "$id" >>"$work/incomplete"
			printf '%s\t%s\n' "$uri" "$path" >>"$dir/failures.tsv"
		fi
	done <"$work/plan"

	# the manifest: other slices' lines kept, this slice's rewritten
	awk -F'\t' -v OFS='\t' -v slice="$slice" '
		FILENAME == ARGV[1] { bad[$1] = 1; next }
		FILENAME == ARGV[2] { if ($1 == "item" && !($2 in bad)) line[$2] = $2 OFS $3 OFS ($4 == "-" ? "" : $4); next }
		index($1, slice) != 1 { print }
		END { for (id in line) print line[id] }' \
		"$work/incomplete" "$work/plan" "$dir/manifest.tsv" | sort >"$work/manifest"
	mv "$work/manifest" "$dir/manifest.tsv"
	echo "slice $slice: $(grep -c "^$slice" "$dir/manifest.tsv" || true) manifestations complete," \
		"$(sort -u "$work/incomplete" | wc -l | tr -d ' ') incomplete" >&2
done

echo "$(wc -l <"$dir/manifest.tsv" | tr -d ' ') manifestations in $dir;" \
	"$(wc -l <"$dir/failures.tsv" | tr -d ' ') failures, $(wc -l <"$dir/mismatches.tsv" | tr -d ' ') SHA-1 mismatches" >&2
