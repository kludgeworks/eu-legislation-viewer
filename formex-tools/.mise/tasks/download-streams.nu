#!/usr/bin/env -S nu
#MISE description="Download content streams listed in a file using Nushell"
#USAGE flag "-i --input <input>" default="content-streams.txt"
#USAGE flag "-o --output <output>" default="downloads"

# Downloads a single URL, saves it, and returns a status record.
def download-url [
  url: string       # The URL to download
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
  let target_dir = (
    [
      $out_dir
      $host
      ...$path_prefix
    ]
    | path join
  )
  let target_path = (
    [
      $target_dir
      $file_name
    ]
    | path join
  )

  # 2. Skip if the file already exists
  if ($target_path | path exists) {
    return {
      url: $url,
      path: $target_path,
      status: "skipped",
      message: "file exists"
    }
  }

  # 3. Ensure the target directory exists
  mkdir $target_dir

  # 4. Perform the download with detailed error logging
  let start_time = (date now)
  let response = try {
    http get --max-time 120sec --full --raw $url
  } catch {|err|
    let end_time = (date now)
    let duration_ns = (($end_time - $start_time) | into int)
    let duration_ms = ($duration_ns / 1_000_000)
    let msg = $err.msg? | default "Unknown network error"

    # Create detailed error message with timing and context
    let detailed_msg = $"($msg) \(after ($duration_ms)ms, URL: ($url | str substring 0..100)\)"

    return {
      url: $url,
      path: $target_path,
      status: "error",
      message: $detailed_msg,
      error_type: "network_timeout_or_failure",
      duration_ms: $duration_ms
    }
  }

  # 5. Handle HTTP errors with detailed information
  if $response.status >= 400 {
    let end_time = (date now)
    let duration_ns = (($end_time - $start_time) | into int)
    let duration_ms = ($duration_ns / 1_000_000)

    # Map common HTTP status codes to readable messages
    let status_msg = match $response.status {
      400 => "Bad Request",
      401 => "Unauthorized",
      403 => "Forbidden",
      404 => "Not Found",
      429 => "Too Many Requests (Rate Limited)",
      500 => "Internal Server Error",
      502 => "Bad Gateway",
      503 => "Service Unavailable",
      504 => "Gateway Timeout",
      _ => "HTTP Error"
    }

    return {
      url: $url,
      path: $target_path,
      status: "error",
      message: $"($status_msg) \(HTTP ($response.status), after ($duration_ms)ms\)",
      error_type: "http_error",
      http_status: $response.status,
      duration_ms: $duration_ms
    }
  }

  # 6. Try to format as XML, otherwise save the raw body
  let content_to_save = try {
      $response.body | from xml | to xml --indent 2
  } catch {
      $response.body # Fallback to raw content if XML parsing fails
  }

  $content_to_save | save --force --raw $target_path

  # Calculate final timing
  let end_time = (date now)
  let duration_ns = (($end_time - $start_time) | into int)
  let duration_ms = ($duration_ns / 1_000_000)

  {
    url: $url,
    path: $target_path,
    status: "ok",
    message: $"downloaded \(($duration_ms)ms\)",
    duration_ms: $duration_ms
  }
}

# Calculate conservative number of threads to avoid network saturation
def calculate-jobs [total_urls: int] {
    # Detect CPU cores with fallbacks
    let cpu_cores = try {
        # Try sysctl first (works on macOS and some Unix systems)
        run-external "sysctl" "-n" "hw.ncpu" | str trim | into int
    } catch {
        try {
            # Try nproc (works on Linux)
            run-external "nproc" | str trim | into int
        } catch { 8 }  # Conservative fallback
    }

    # Conservative base: Use CPU cores directly (not multiplied)
    # Network-bound operations benefit more from being polite to servers
    let base_threads = $cpu_cores

    # Very conservative workload scaling to prevent network saturation
    let workload_multiplier = match $total_urls {
        $it if $it < 50 => 0.5
        $it if $it < 500 => 0.75
        $it if $it < 2000 => 1.0
        _ => 1.25
    }

    let calculated = ($base_threads * $workload_multiplier | math round)

    # Bounds: 2 minimum, 12 maximum
    # This prevents overwhelming remote servers
    let bounded = ([$calculated 2] | math max)
    [$bounded 12] | math min
}



# Progress bar wrapper using pv for visual feedback
def download-progress [total: int]: any -> any {
    tee { to text | pv --bar-style granular -l -s $total -N "Chunks" | ignore }
}


# Main entry point for the script
def main [
    --input(-i): string = "content-streams.txt"   # Input file with URLs to download
    --output(-o): string = "downloads"            # Output directory for downloads
] {
    if not ($input | path exists) {
        error make {
            msg: $"Input file not found: ($input)"
        }
    }
    mkdir $output

    # Read, trim, and filter URLs from the input file
    let urls = (
        open --raw $input
        | lines
        | each {|line| $line | str trim }
        | where {|it| $it != "" and not ($it | str starts-with '#') }
    )
    let total = ($urls | length)

    if ($total == 0) {
        return
    }

    # Calculate optimal number of jobs based on system and workload
    let jobs = (calculate-jobs $total)
    let chunk_size = $jobs
    let total_chunks = ($total / $chunk_size | math ceil)

    # Process URLs in chunks with pv progress bar
    let results = (
        $urls
        | chunks $chunk_size
        | enumerate  
        | download-progress $total_chunks
        | each {|chunk_info|
            # Process each chunk in parallel
            $chunk_info.item | par-each --threads $jobs {|url|
                download-url $url $output
            }
        }
        | flatten
    )

    # Output errors as JSONL to stdout
    let errors = ($results | where status == 'error')
    $errors | each {|err| $err | to json -r | print }

    # Exit with error code if there were any failures
    if ($errors | is-not-empty) {
        exit 1
    }
}
