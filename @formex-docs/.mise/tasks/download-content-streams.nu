#!/usr/bin/env nu
#MISE description="Download content streams listed in content-streams.txt using Nushell"
#MISE usage="[input_file] [output_dir] [threads]"

# Downloads URLs in parallel using Nushell's http client and formats responses as XML.
# Defaults: input_file=content-streams.txt, output_dir=downloads-nu, threads=4.

def read-urls [file_path: string] {
  open --raw $file_path
    | lines
    | enumerate
    | where { |row|
        let clean = ($row.item | str trim)
        $clean != "" and not ($clean | str starts-with "#") }
    | update item { |row| $row.item | str trim }
}

def download-entry [entry out_dir: string] {
  let idx = $entry.index + 1
  let url = $entry.item
  let parsed = ($url | url parse)
  let host = ($parsed.host | default "unknown-host")
  let raw_path = ($parsed.path | default "")
  let segments = ($raw_path | str trim --char "/" | split row "/" | where { |seg| $seg != "" })

  let file_stem = (if ($segments | is-empty) { "index" } else { $segments | last })
  let file_name = ($file_stem + ".xml")
  let dir_segments = (if ($segments | is-empty) { [] } else { $segments | drop 1 })
  let dir_components = ([$out_dir, $host] ++ $dir_segments)
  let target_dir = ($dir_components | path join)
  let target = ([ $target_dir, $file_name ] | path join)

  if ($target | path exists) {
    return { index: $idx url: $url path: $target status: "skipped" code: null message: null }
  }

  let target_dir = ($target | path dirname)
  mkdir $target_dir

  let response = (try {
        http get --max-time 120sec --full --raw $url
      } catch { |err|
        { err: ($err.msg? | default ($err | into string)) }
      })

  let err_msg = ($response | get err? | default null)
  if ($err_msg != null) {
    return { index: $idx url: $url path: $target status: "error" code: null message: $err_msg }
  }

  let status = ($response.status | default 0)

  if ($status == 404 or $status == 500) {
    return { index: $idx url: $url path: $target status: "skipped" code: $status message: null }
  }

  if ($status >= 400) {
    return { index: $idx url: $url path: $target status: "error" code: $status message: $"HTTP ($status)" }
  }

  let body = ($response.body | default "")

  let formatted = (try {
        $body | from xml | to xml --indent 2
      } catch { null })

  if ($formatted == null) {
    $body | save --force --raw $target
    { index: $idx url: $url path: $target status: "ok" code: $status message: "saved raw (formatting failed)" }
  } else {
    $formatted | save --force --raw $target
    { index: $idx url: $url path: $target status: "ok" code: $status message: null }
  }
}

def report-results [results manifest_path: string] {
  $results
    | select index url path status code message
    | to json
    | save --force $manifest_path

  let errors = ($results | where status == "error")

  if ($errors | is-empty) {
    print $"All downloads completed. Manifest: ($manifest_path)"
  } else {
    print "Some downloads failed:"
    $errors | each { |row| print $"- ($row.url): ($row.message | default "error")" }
    exit 1
  }
}

def main [
  input: path = "content-streams.txt"
  out_dir: path = "downloads-nu"
  jobs: int = 4
] {
  if ($jobs < 1) {
    error make { msg: "jobs must be >= 1" }
  }

  let input_path = ($input | into string)
  if not ($input_path | path exists) {
    error make { msg: $"Input file not found: ($input_path)" }
  }

  let out_dir_path = ($out_dir | into string)
  mkdir $out_dir_path

  let urls = (read-urls $input_path)

  if ($urls | is-empty) {
    print "No URLs to download."
    return 0
  }

  let results = ($urls
    | par-each --threads $jobs { |row| download-entry $row $out_dir_path }
    | collect)

  let manifest_path = ([$out_dir_path, "manifest.json"] | path join)
  report-results $results $manifest_path
}
