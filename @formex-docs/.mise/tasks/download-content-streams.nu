#!/usr/bin/env -S nu
#MISE description="Download content streams listed in a file using Nushell"
##USAGE flag "-f --file" default="content-streams.txt"
##USAGE flag "-o --output" default="downloads"
##USAGE flag "-j --jobs" default="4"

env
# Downloads a single URL, saves it, and returns a status record.
def "download-url" [
  url: string,      # The URL to download
  out_dir: string   # The root directory for downloads
] {
  # 1. Determine the output path based on the URL structure
  let parsed_url = ($url | url parse)
  let host = $parsed_url.host? | default "unknown-host"
  let path_parts = (
    $parsed_url.path
    | path split
    | where {|it| $it != "/" and $it != "" }
  )

  let file_stem = if ($path_parts | length) == 0 { "index" } else { $path_parts | last }
  let file_name = $"($file_stem).xml"

  let path_prefix = if ($path_parts | length) > 1 { $path_parts | take (($path_parts | length) - 1) } else { [] }
  let target_dir = ([$out_dir, $host] ++ $path_prefix) | path join
  let target_path = [$target_dir, $file_name] | path join

  # 2. Skip if the file already exists
  if ($target_path | path exists) {
    return { url: $url, path: $target_path, status: "skipped", message: "file exists" }
  }

  # 3. Ensure the target directory exists
  mkdir $target_dir

  # 4. Perform the download
  let response = try {
    http get --max-time 120sec --full --raw $url
  } catch { |err|
    let msg = $err.msg? | default "Unknown network error"
    return { url: $url, path: $target_path, status: "error", message: $msg }
  }

  # 5. Handle HTTP errors
  if $response.status >= 400 {
    return { url: $url, path: $target_path, status: "error", message: $"HTTP ($response.status)" }
  }

  # 6. Try to format as XML, otherwise save the raw body
  let content_to_save = try {
      $response.body | from xml | to xml --indent 2
  } catch {
      $response.body # Fallback to raw content if XML parsing fails
  }

  $content_to_save | save --force --raw $target_path

  { url: $url, path: $target_path, status: "ok", message: "downloaded" }
}

# Main entry point for the script
def main [
  --file(-f): string = "content-streams.txt"
  --output(-o): string = "downloads"
  --jobs(-j): int = 4
] {
  let input = $file
  let out_dir = $output
  let jobs = $jobs

  print $"jobs: ($jobs)"
  if not ($input | path exists) {
    error make { msg: $"Input file not found: ($input)" }
  }
  mkdir $out_dir

  # Read, trim, and filter URLs from the input file
  let urls = (
    open --raw $input
    | lines
    | each {|line| $line | str trim }
    | where {|it| $it != "" and not ($it | str starts-with '#') }
  )
  let total = ($urls | length)

  if ($total == 0) {
    print "No valid URLs found to download."
    return
  }

  print $"Downloading ($total) URLs with ($jobs) parallel jobs..."

  # Process URLs in chunks so we can echo progress updates as we go
  let chunk_percent = 0.05
  let chunk_size = (
    [($total * $chunk_percent) 1]
    | math max
    | math ceil
    | into int
  )

  mut completed = 0
  mut results = []

  for chunk in ($urls | chunks $chunk_size) {
    let chunk_results = (
      $chunk
      | par-each --threads $jobs {|url| download-url $url $out_dir }
      | collect
    )

    let chunk_count = ($chunk_results | length)
    $completed += $chunk_count

    let percent = (
      (($completed | into float) / ($total | into float)) * 100
      | math round --precision 1
    )

    print $"($completed) / ($total) \\(($percent)%\\) completed"

    mut results = ($results | append $chunk_results)
  }

  $results | each {|res| print $"[($res.status | str upcase)] ($res.url)" }

  # Save a manifest of all operations
  let manifest_path = [$out_dir, "manifest.json"] | path join
  $results | to json --indent 2 | save --force $manifest_path

  # Final report based on collected results
  let errors = ($results | where status == 'error')
  if ($errors | is-empty) {
    print -e $"\n✅ All downloads completed successfully."
  } else {
    print -e $"\n❌ Encountered ($errors | length) errors:"
    $errors | each {|err| print -e $"- ($err.url): ($err.message)" }
    exit 1
  }
  print $"Manifest written to ($manifest_path)"
}
