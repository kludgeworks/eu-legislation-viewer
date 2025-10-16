#!/usr/bin/env bash
#/// script
#/// summary: Download URLs from content-streams.txt, save as .xml, and pretty print when possible

set -euo pipefail

input_file="content-streams.txt"
base_dir="${DOWNLOAD_DIR:-downloads}"

if [[ ! -f "${input_file}" ]]; then
  echo "${input_file} not found" >&2
  exit 1
fi

mkdir -p "${base_dir}"

while IFS= read -r url || [[ -n "${url}" ]]; do
  [[ -z "${url}" ]] && continue
  [[ "${url}" =~ ^# ]] && continue

  relative_path="${url#*://}"
  output_path="${base_dir}/${relative_path}.xml"
  tmp_path="${output_path}.download"
  output_dir="$(dirname "${output_path}")"

  mkdir -p "${output_dir}"

  if [[ -f "${output_path}" ]]; then
    printf 'Skipping existing file: %s\n' "${output_path}"
    continue
  fi

  printf 'Downloading %s -> %s\n' "${url}" "${output_path}"

  if ! curlie \
    --fail \
    --location \
    --progress-bar \
    --request GET \
    --header 'Accept: */*' \
    --user-agent 'curl/8.5.0' \
    --output "${tmp_path}" \
    "${url}" < /dev/null; then
    printf '\nFailed to download %s\n' "${url}" >&2
    rm -f "${tmp_path}"
    exit 1
  fi

  formatted=false

  if command -v xmllint >/dev/null 2>&1; then
    if xmllint --format "${tmp_path}" --output "${output_path}"; then
      formatted=true
      rm -f "${tmp_path}"
      printf 'Formatted %s with xmllint\n' "${output_path}"
    else
      printf 'xmllint formatting failed for %s, falling back to raw output\n' "${output_path}" >&2
    fi
  fi

  if [[ "${formatted}" == false ]]; then
    mv "${tmp_path}" "${output_path}"
  fi

  printf 'Saved %s\n' "${output_path}"
done < "${input_file}"
