#!/usr/bin/env bash
#/// script
#/// summary: Fetch English FMX4 manifestations via Java preview run

set -euo pipefail

java --enable-preview fetch_english_fmx4_manifestations.java
