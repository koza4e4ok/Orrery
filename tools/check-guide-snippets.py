#!/usr/bin/env python3
"""Checks that guide snippets marked as sample code are copied from it exactly.

A fenced block right after a `<!-- snippet: <path> -->` line must appear verbatim in <path>
(relative to the repository root), so a snippet cannot drift from code that CI compiles.
"""
import pathlib
import re
import sys

MARKER = re.compile(r"^<!-- snippet: (\S+) -->$")
root = pathlib.Path(__file__).resolve().parent.parent
failures = 0
checked = 0
for name in sys.argv[1:]:
    lines = pathlib.Path(name).read_text(encoding="utf-8").splitlines()
    for i, line in enumerate(lines):
        match = MARKER.match(line)
        if not match:
            continue
        if i + 1 >= len(lines) or not lines[i + 1].startswith("```"):
            print(f"{name}:{i + 1}: snippet marker is not followed by a code block")
            failures += 1
            continue
        end = next((j for j in range(i + 2, len(lines)) if lines[j] == "```"), None)
        if end is None:
            print(f"{name}:{i + 1}: unterminated code block")
            failures += 1
            continue
        block = "\n".join(lines[i + 2 : end]) + "\n"
        source = root / match.group(1)
        checked += 1
        if not source.is_file() or block not in source.read_text(encoding="utf-8"):
            print(f"{name}:{i + 3}: snippet does not match {match.group(1)}")
            failures += 1
print(f"{checked} snippet(s) checked, {failures} problem(s)")
sys.exit(1 if failures else 0)
