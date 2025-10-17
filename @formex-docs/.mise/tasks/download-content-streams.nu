#!/usr/bin/env nu
#MISE description="Download content streams listed in content-streams.txt using Nushell"
#MISE usage="[input_file] [output_dir] [threads]"

# Downloads URLs in parallel using Nushell's http client and formats responses as XML.
# Defaults: input_file=content-streams.txt, output_dir=downloads-nu, threads=4.

def emit-status [result] {
  let status = ($result.status | default "unknown")
  let url = ($result.url | default "")
  let message = ($result.message? | default "")

  if ($message | is-empty) {
    print $"($status)\t($url)"
  } else {
    print $"($status)\t($url)\t($message)"
  }

  $result
}

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
    return (emit-status {
      index: $idx
      url: $url
      path: $target
      status: "skipped"
      code: null
      message: "exists"
    })
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
    return (emit-status {
      index: $idx
      url: $url
      path: $target
      status: "error"
      code: null
      message: $err_msg
    })
  }

  let status = ($response.status | default 0)

  if ($status == 404 or $status == 500) {
    return (emit-status {
      index: $idx
      url: $url
      path: $target
      status: "skipped"
      code: $status
      message: $"http $status"
    })
  }

  if ($status >= 400) {
    return (emit-status {
      index: $idx
      url: $url
      path: $target
      status: "error"
      code: $status
      message: $"http $status"
    })
  }

  let body = ($response.body | default "")

  let formatted = (try {
        $body | from xml | to xml --indent 2
      } catch { null })

  if ($formatted == null) {
    $body | save --force --raw $target
    return (emit-status {
      index: $idx
      url: $url
      path: $target
      status: "ok"
      code: $status
      message: "raw"
    })
  } else {
    $formatted | save --force --raw $target
    return (emit-status {
      index: $idx
      url: $url
      path: $target
      status: "ok"
      code: $status
      message: ""
    })
  }
}

def report-results [results manifest_path: string] {
  $results
    | select index url path status code message
    | to json
    | save --force $manifest_path

  let errors = ($results | where status == "error")

  if ($errors | is-empty) {
    print -e $"All downloads completed. Manifest: ($manifest_path)"
  } else {
    print -e "Some downloads failed:"
    $errors | each { |row| print -e $"- ($row.url): ($row.message | default "error")" }
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
    print -e "No URLs to download."
    return 0
  }

  if (which pv | is-empty) {
    print -e "Warning: pv not found in PATH. Install it (e.g. 'brew install pv') for progress bars."
  }

  let total = ($urls | length)

  let results = ($urls
    | enumerate
    | par-each --threads $jobs { |pair|
        download-entry $pair.item $out_dir_path
      }
    | collect)

  let manifest_path = ([$out_dir_path, "manifest.json"] | path join)
  report-results $results $manifest_path
}
