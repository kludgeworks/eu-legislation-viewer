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

function bail_if_disk_full() {
  local size_bytes
  size_bytes=$(dir_size_bytes)

  if [[ -z "${size_bytes}" ]]; then
    printf 'Unable to determine size of %s\n' "${base_dir}" >&2
    exit 1
  fi

  local max_bytes=$((150 * 1024 * 1024 * 1024))
  if (( size_bytes > max_bytes )); then
    printf 'Download directory exceeds 150GB (%s bytes) — aborting\n' "${size_bytes}" >&2
    exit 1
  fi
}

function dir_size_bytes() {
  local size=""

  if command -v gdu >/dev/null 2>&1; then
    size=$(gdu -sb "${base_dir}" 2>/dev/null | awk '{print $1}')
  else
    size=$(du -sb "${base_dir}" 2>/dev/null | awk '{print $1}' || true)
  fi

  if [[ -z "${size}" ]]; then
    local size_kb
    size_kb=$(du -sk "${base_dir}" 2>/dev/null | awk '{print $1}')
    if [[ -n "${size_kb}" ]]; then
      size=$((size_kb * 1024))
    fi
  fi

  printf '%s' "${size}"
}

bail_if_disk_full

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

  set +e
  status_code=$(curlie \
    --location \
    --progress-bar \
    --request GET \
    --header 'Accept: */*' \
    --user-agent 'curl/8.5.0' \
    --output "${tmp_path}" \
    --write-out '%{http_code}' \
    "${url}" < /dev/null)
  curl_status=$?
  set -e

  status_code=$(printf '%s' "${status_code}" | tr -dc '0-9')

  if [[ -z "${status_code}" ]]; then
    rm -f "${tmp_path}"
    printf 'Unable to determine HTTP status for %s\n' "${url}" >&2
    exit 1
  fi

  if (( curl_status != 0 )); then
    rm -f "${tmp_path}"
    printf 'Failed to download %s (exit: %d)\n' "${url}" "${curl_status}" >&2
    exit 1
  fi

  if [[ "${status_code}" == "404" ]]; then
    rm -f "${tmp_path}"
    printf 'Not found (404): %s\n' "${url}"
    continue
  fi

  if [[ "${status_code}" == "500" ]]; then
    rm -f "${tmp_path}"
    printf 'Server error (500): %s\n' "${url}"
    continue
  fi

  if [[ "${status_code}" =~ ^[0-9]+$ ]] && (( status_code >= 400 )); then
    rm -f "${tmp_path}"
    printf 'Unexpected status %s for %s\n' "${status_code}" "${url}" >&2
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

  bail_if_disk_full
done < "${input_file}"
