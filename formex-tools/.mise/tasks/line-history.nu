#!/usr/bin/env nu
#MISE description="Extract and group fmx:line.history data by date from a Formex XSD file"
#USAGE flag "-f --format <format>" help="Output format" default="table" {
#USAGE   choices "table" "json" "jsonl"
#USAGE }
#USAGE arg file "Path to the Formex XSD file (e.g. xsd/formex-05.55-20141201.xd)"

# Command: decode line history sections and present grouped by date
def main [file: string, --format(-f): string = "table"] {

  if not ($file | path exists) {
    error make { msg: $"File not found: ($file)" }
  }

  # Define namespaces
  let namespaces = {fmx: "http://opoce"}

  # Extract line.history entries and parse dates
  let history_entries = open --raw $file
    | query xml '//fmx:line.history' --namespaces $namespaces
    | each {|line|
        let text = ($line.string_value | str trim)
        # Check if line starts with a date (8 digits)
        let matches = ($text | parse --regex '^(?<date>\d{8})\s*(?<desc>.*)$')

        if ($matches | is-empty) {
            {
                date: null,
                text: $text
            }
        } else {
            {
                date: $matches.0.date,
                text: $matches.0.desc
            }
        }
    }
    | reduce -f {
        current_date: null,
        result: []
    } {|it, acc|
        # Carry forward the last seen date to lines without dates
        let new_date = if $it.date != null { $it.date } else { $acc.current_date }

        {
            current_date: $new_date,
            result: (
                $acc.result
                | append {
                    date: $new_date,
                    text: $it.text
                }
            )
        }
    }
    | get result
    | group-by date
    | transpose date entries
    | each {|group|
        {
          date: $group.date,
          lines: $group.entries.text
        }
    }

  # Format output
  match $format {
    "json" => { $history_entries | to json }
    "jsonl" => { $history_entries | each { to json } | str join (char newline) }
    "table" => {
      $history_entries
      | each {|entry|
          {
            date: $entry.date,
            lines: ($entry.lines | str join (char newline))
          }
      }
      | table --index 1 --theme "restructured"
    }
    _ => {
      error make { msg: $"Unknown format: ($format)" }
    }
  }
}
