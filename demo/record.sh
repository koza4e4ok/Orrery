#!/usr/bin/env bash
# TEMPORARY: records the demo tour; <output>.loop holds the length of one loop.
# Usage: record.sh <app log> <output video> <recorder command...>
# The recorder command gets the output path appended and is stopped with SIGINT.
set -euo pipefail
log=$1
out=$2
shift 2
touch "$log"
# BSD date on macOS has no %N, so take sub-second time from perl.
now() { perl -MTime::HiRes=time -e 'printf "%.3f\n", time'; }
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
# Start recording after the first loop, so fonts, shaders and caches are warm, and keep
# recording through loops 2 and 3: recorders such as simctl recordVideo start several
# seconds late. The length of loop 3 goes to <output>.loop so the GIF step can keep
# exactly the last loop of the video.
wait_for 1
"$@" "$out" &
rec=$!
wait_for 2
start=$(now)
wait_for 3
end=$(now)
awk -v a="$start" -v b="$end" 'BEGIN { printf "%.2f\n", b - a }' > "$out.loop"
kill -INT "$rec"
# simctl recordVideo can hang on SIGINT; give it 30 s to finalize the file, then kill it.
for _ in $(seq 1 300); do
  kill -0 "$rec" 2>/dev/null || break
  sleep 0.1
done
kill -9 "$rec" 2>/dev/null || true
wait "$rec" || true
ls -l "$out"
