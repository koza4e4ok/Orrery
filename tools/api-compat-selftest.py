#!/usr/bin/env python3
"""Selftest for tools/api-compat.py with the fixture dumps in tools/api-compat-selftest/.

`base/` holds the baseline dumps. Each `cases/<name>/` builds a head tree from `base/` plus
the files in its `head/` overlay minus the paths in `delete.txt`, runs the gate with its
optional `allow.txt` and checks `case.json`: `exit`, `contains` (stdout), `stderr`, `args`
and `baseline_version` (default `v0.9.2`). The tests below the cases cover `--emit-removed`,
the job summary, version ordering and baseline refs resolved from a real git repository.
Run: python3 tools/api-compat-selftest.py
"""
import contextlib
import importlib.util
import io
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
FIXTURES = HERE / "api-compat-selftest"
BASE = FIXTURES / "base"

sys.dont_write_bytecode = True  # keep tools/ free of __pycache__
spec = importlib.util.spec_from_file_location("api_compat", HERE / "api-compat.py")
api_compat = importlib.util.module_from_spec(spec)
spec.loader.exec_module(api_compat)


def run_gate(argv, env=None):
    out, err = io.StringIO(), io.StringIO()
    saved = dict(os.environ)
    os.environ.pop("API_BASELINE_REF", None)
    os.environ.pop("GITHUB_STEP_SUMMARY", None)
    os.environ.update(env or {})
    try:
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = api_compat.main(argv)
    finally:
        os.environ.clear()
        os.environ.update(saved)
    return code, out.getvalue(), err.getvalue()


def build_head(case_dir, target):
    shutil.copytree(BASE, target)
    overlay = case_dir / "head"
    if overlay.is_dir():
        shutil.copytree(overlay, target, dirs_exist_ok=True)
    delete = case_dir / "delete.txt"
    if delete.exists():
        for rel in delete.read_text(encoding="utf-8").split():
            (target / rel).unlink()


class Cases(unittest.TestCase):
    maxDiff = None


def make_case(case_dir):
    def test(self):
        expected = json.loads((case_dir / "case.json").read_text(encoding="utf-8"))
        with tempfile.TemporaryDirectory() as tmp:
            head = Path(tmp) / "head"
            build_head(case_dir, head)
            allow = case_dir / "allow.txt"
            if not allow.exists():
                allow = Path(tmp) / "empty-allow.txt"
                allow.write_text("", encoding="utf-8")
            argv = ["--baseline-dir", str(BASE), "--head-dir", str(head),
                    "--baseline-version", expected.get("baseline_version", "v0.9.2"),
                    "--allow-list", str(allow), "--no-summary", *expected.get("args", [])]
            code, out, err = run_gate(argv)
        context = f"\n--- stdout ---\n{out}\n--- stderr ---\n{err}"
        self.assertEqual(expected["exit"], code, context)
        for text in expected.get("contains", []):
            self.assertIn(text, out, context)
        for text in expected.get("stderr", []):
            self.assertIn(text, err, context)
    return test


for case_dir in sorted((FIXTURES / "cases").iterdir()):
    setattr(Cases, "test_" + case_dir.name.replace("-", "_"), make_case(case_dir))


class EmitRemoved(unittest.TestCase):
    maxDiff = None

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.head = self.tmp / "head"
        build_head(FIXTURES / "emit", self.head)
        self.empty = self.tmp / "empty.txt"
        self.empty.write_text("", encoding="utf-8")

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def gate(self, allow, *extra):
        return run_gate(["--baseline-dir", str(BASE), "--head-dir", str(self.head), "--baseline-version",
                         "v0.9.2", "--allow-list", str(allow), "--no-summary", *extra])

    def test_prints_allow_list_entries_grouped_by_class(self):
        code, out, _ = self.gate(self.empty, "--emit-removed", "--reason", "1.0-dataclass",
                                 "--ledger", "ledger 2.4")
        self.assertEqual(0, code)
        self.assertEqual((FIXTURES / "emit" / "expected.txt").read_text(encoding="utf-8"), out)

    def test_emitted_entries_make_the_gate_pass(self):
        code, out, _ = self.gate(self.empty)
        self.assertEqual(1, code, out)
        _, emitted, _ = self.gate(self.empty, "--emit-removed")
        allow = self.tmp / "allow.txt"
        allow.write_text(emitted, encoding="utf-8")
        code, out, _ = self.gate(allow)
        self.assertEqual(0, code, out)
        self.assertIn("lib-a: removed 0, changed 0, lost targets 0, allowed 10, added 0", out)
        self.assertNotIn("stale", out)

    def test_job_summary_lists_counts_per_module(self):
        summary = self.tmp / "summary.md"
        code, _, _ = run_gate(["--baseline-dir", str(BASE), "--head-dir", str(self.head), "--baseline-version",
                               "v0.9.2", "--allow-list", str(self.empty), "--report"],
                              env={"GITHUB_STEP_SUMMARY": str(summary)})
        self.assertEqual(0, code)
        text = summary.read_text(encoding="utf-8")
        self.assertIn("| lib-a | 10 | 0 | 0 | 0 | 0 |", text)
        self.assertIn("(report mode)", text)


class Versions(unittest.TestCase):
    def test_semver_precedence(self):
        ordered = ["v0.9.2", "v0.9.10", "v1.0.0-alpha", "v1.0.0-beta.2", "v1.0.0-beta.11",
                   "v1.0.0-rc.1", "v1.0.0-rc.2", "v1.0.0", "v1.0.1", "v1.1.0-rc.1", "v1.1.0"]
        keys = [api_compat.parse_version(v) for v in ordered]
        self.assertEqual(keys, sorted(keys))
        self.assertIsNone(api_compat.parse_version("main"))
        self.assertIsNone(api_compat.parse_version("v1.0"))


class GitBaseline(unittest.TestCase):
    """Baseline refs read from a real repository: default tag, $API_BASELINE_REF, --previous-of."""

    def setUp(self):
        self.repo = Path(tempfile.mkdtemp())
        self.git("init", "-q")
        shutil.copytree(BASE, self.repo, dirs_exist_ok=True)
        (self.repo / "tools").mkdir()
        (self.repo / "tools" / "api-compat-allow.txt").write_text("# none\n", encoding="utf-8")
        self.commit("baseline")
        self.git("tag", "v0.9.2")
        jvm = self.repo / "lib-a" / "api" / "lib-a.api"
        jvm.write_text(jvm.read_text().replace("\tpublic final fun baz (Ljava/lang/String;)V\n", ""))
        self.commit("drop baz")
        self.git("tag", "v0.9.10")
        (self.repo / "README").write_text("later\n")
        self.commit("untagged head")

    def tearDown(self):
        shutil.rmtree(self.repo)

    def git(self, *args):
        subprocess.run(["git", "-c", "user.name=t", "-c", "user.email=t@example.com", *args],
                       cwd=self.repo, check=True, capture_output=True)

    def commit(self, message):
        self.git("add", "-A")
        self.git("commit", "-q", "-m", message)

    def gate(self, *args, env=None):
        return run_gate(["--root", str(self.repo), "--no-summary", *args], env=env)

    def test_default_baseline_is_the_highest_semver_tag(self):
        code, out, _ = self.gate()
        self.assertEqual(0, code, out)
        self.assertIn("baseline v0.9.10,", out)
        self.assertIn("api-compat-allow.txt (0 entries)", out)

    def test_default_baseline_skips_a_tag_at_head(self):
        self.git("checkout", "-q", "v0.9.10")
        code, out, _ = self.gate()
        self.assertEqual(1, code, out)
        self.assertIn("baseline v0.9.2,", out)

    def test_env_baseline_ref(self):
        code, out, _ = self.gate(env={"API_BASELINE_REF": "v0.9.2"})
        self.assertEqual(1, code, out)
        self.assertIn("REMOVED lib-a.api: com.example.a.Foo :: public final fun baz (Ljava/lang/String;)V", out)

    def test_previous_of_a_tag(self):
        code, out, _ = self.gate("--previous-of", "v0.9.10")
        self.assertEqual(1, code, out)
        self.assertIn("baseline v0.9.2,", out)

    def test_sha_baseline_uses_the_nearest_tag_for_the_rc_rule(self):
        self.git("tag", "v1.0.0-rc.1", "v0.9.10")
        (self.repo / "tools" / "api-compat-allow.txt").write_text(
            "lib-a | class com.example.a.Foo | 1.0-dataclass | row 1\n", encoding="utf-8")
        sha = subprocess.run(["git", "rev-parse", "v0.9.10"], cwd=self.repo, check=True,
                             capture_output=True, text=True).stdout.strip()
        code, out, _ = self.gate("--baseline-ref", sha)
        self.assertEqual(1, code, out)
        self.assertIn("allow-list must be empty", out)

    def test_rc_tag_baseline_needs_empty_allow_list(self):
        self.git("tag", "v1.0.0-rc.1", "v0.9.10")
        (self.repo / "tools" / "api-compat-allow.txt").write_text(
            "lib-a | class com.example.a.Foo | 1.0-dataclass | row 1\n", encoding="utf-8")
        code, out, _ = self.gate("--baseline-ref", "v1.0.0-rc.1")
        self.assertEqual(1, code, out)
        self.assertIn("FAILED: baseline v1.0.0-rc.1 is 1.0.0-rc.1 or later", out)

    def test_missing_baseline_ref_is_a_setup_error(self):
        code, _, err = self.gate("--baseline-ref", "v9.9.9")
        self.assertEqual(2, code)
        self.assertIn("git fetch --no-tags --depth=1 origin refs/tags/v9.9.9:refs/tags/v9.9.9", err)


if __name__ == "__main__":
    unittest.main(verbosity=2)
