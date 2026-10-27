#!/usr/bin/env bash
# TEMPORARY: records exactly one loop of the demo tour.
# Usage: record.sh <app log> <output video> <recorder command...>
# The recorder command gets the output path appended and is stopped with SIGINT.
set -euo pipefail
log=$1
out=$2
shift 2
touch "$log"
count() { grep -c ORRERY_DEMO_LOOP "$log" 2>/dev/null || true; }
wait_for() {
  for _ in $(seq 1 3000); do
    [ "$(count)" -ge "$1" ] && return 0
    sleep 0.1
  done
  echo "Timed out waiting for demo loop $1"
  tail -n 80 "$log"
  exit 1
}
# Skip the first loop so fonts, shaders and caches are warm.
wait_for 2
"$@" "$out" &
rec=$!
wait_for 3
kill -INT "$rec"
wait "$rec" || true
ls -l "$out"
