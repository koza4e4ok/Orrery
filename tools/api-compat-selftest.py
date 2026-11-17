#!/usr/bin/env python3
"""Selftest for tools/api-compat.py with the fixture dumps in tools/api-compat-selftest/.

`base/` holds the baseline dumps. Each `cases/<name>/` builds a head tree from `base/` plus
the files in its `head/` overlay minus the paths in `delete.txt`, runs the gate with its
optional `allow.txt` and checks `case.json`: `exit`, `contains` and `absent` (stdout),
`stderr`, `args`, `env` and `baseline_version` (default `v0.9.2`, `null` for none). The tests below the cases cover `--emit-removed`, `--ledger-map` and the ledger
row check, nested classes (`nested/`, `Outer$Inner`),
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
    os.environ.pop("GITHUB_ACTIONS", None)
    os.environ.update(env or {})
    try:
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            try:
                code = api_compat.main(argv)
            except SystemExit as e:  # argparse rejects a bad option value
                code = e.code
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
            version = expected.get("baseline_version", "v0.9.2")
            argv = ["--baseline-dir", str(BASE), "--head-dir", str(head),
                    *(["--baseline-version", version] if version else []),
                    "--allow-list", str(allow), "--no-summary", *expected.get("args", [])]
            code, out, err = run_gate(argv, env=expected.get("env"))
        context = f"\n--- stdout ---\n{out}\n--- stderr ---\n{err}"
        self.assertEqual(expected["exit"], code, context)
        for text in expected.get("contains", []):
            self.assertIn(text, out, context)
        for text in expected.get("stderr", []):
            self.assertIn(text, err, context)
        for text in expected.get("absent", []):
            self.assertNotIn(text, out, context)
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

    def test_round_trip_emitted_entries_make_the_gate_pass(self):
        # A removal, a whole-class removal and a signature change, fed back as the allow-list.
        code, out, _ = self.gate(self.empty)
        self.assertEqual(1, code, out)
        self.assertIn("lib-a: removed 10, changed 1,", out)
        _, emitted, _ = self.gate(self.empty, "--emit-removed", "--reason", "1.0-dataclass", "--ledger", "row 7")
        allow = self.tmp / "allow.txt"
        allow.write_text(emitted, encoding="utf-8")
        code, out, _ = self.gate(allow)
        self.assertEqual(0, code, out)
        self.assertIn("lib-a: removed 0, changed 0, lost targets 0, allowed 11, added 1", out)
        self.assertIn("OK: compatible", out)
        self.assertNotIn("stale", out)
        self.assertNotIn("FAILED", out)

    def test_unfilled_emitted_entries_cannot_be_committed(self):
        _, emitted, _ = self.gate(self.empty, "--emit-removed")
        self.assertIn("| TODO-reason | TODO-ledger-row", emitted)
        allow = self.tmp / "allow.txt"
        allow.write_text(emitted, encoding="utf-8")
        code, _, err = self.gate(allow)
        self.assertEqual(2, code)
        self.assertIn("reason 'TODO-reason' is not one of", err)

    def test_emit_uses_line_entries_for_a_class_left_in_another_dump(self):
        # Gone is removed from the JVM dump only: class_gone() is false, so no class entry.
        head = self.tmp / "partial"
        build_head(FIXTURES / "cases" / "allow-class-gone-from-jvm-only-fails", head)
        argv = ["--baseline-dir", str(BASE), "--head-dir", str(head), "--baseline-version", "v0.9.2",
                "--no-summary", "--reason", "1.0-dataclass", "--ledger", "row 2"]
        _, emitted, _ = run_gate([*argv, "--allow-list", str(self.empty), "--emit-removed"])
        self.assertNotIn("| class ", emitted)
        self.assertIn("lib-a | com.example.a.Gone :: public final fun ping ()V | 1.0-dataclass | row 2", emitted)
        allow = self.tmp / "partial.txt"
        allow.write_text(emitted, encoding="utf-8")
        code, out, _ = run_gate([*argv, "--allow-list", str(allow)])
        self.assertEqual(0, code, out)
        self.assertIn("allowed 3", out)

    def test_job_summary_lists_counts_per_module(self):
        summary = self.tmp / "summary.md"
        code, _, _ = run_gate(["--baseline-dir", str(BASE), "--head-dir", str(self.head), "--baseline-version",
                               "v0.9.2", "--allow-list", str(self.empty), "--report"],
                              env={"GITHUB_STEP_SUMMARY": str(summary)})
        self.assertEqual(0, code)
        text = summary.read_text(encoding="utf-8")
        self.assertIn("| lib-a | 10 | 1 | 0 | 0 | 1 |", text)
        self.assertIn("(report mode)", text)


class NestedClasses(unittest.TestCase):
    """Nested JVM classes (`Outer$Inner`) and their klib form (`Outer.Inner`) are one class key."""

    BASE = FIXTURES / "nested" / "base"
    HEAD = FIXTURES / "nested" / "head"

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def gate(self, allow_text, *extra):
        allow = self.tmp / "allow.txt"
        allow.write_text(allow_text, encoding="utf-8")
        return run_gate(["--baseline-dir", str(self.BASE), "--head-dir", str(self.HEAD), "--baseline-version",
                         "v0.9.2", "--allow-list", str(allow), "--no-summary", *extra])

    def test_emit_gives_one_class_entry_per_nested_class(self):
        code, out, _ = self.gate("", "--emit-removed", "--reason", "1.0-dataclass", "--ledger", "row 1")
        self.assertEqual(0, code)
        self.assertEqual("# lib-n: com.example.n.Outer.Inner\n"
                         "lib-n | class com.example.n.Outer.Inner | 1.0-dataclass | row 1\n"
                         "# lib-n: com.example.n.Outer.Inner.Deep\n"
                         "lib-n | class com.example.n.Outer.Inner.Deep | 1.0-dataclass | row 1\n", out)

    def test_jvm_and_klib_forms_of_a_nested_class_entry_cover_both_dumps(self):
        code, out, _ = self.gate("lib-n | class com/example/n/Outer$Inner | 1.0-dataclass | row 1\n"
                                 "lib-n | class com.example.n/Outer.Inner.Deep | 1.0-dataclass | row 1\n")
        self.assertEqual(0, code, out)
        self.assertIn("lib-n: removed 0, changed 0, lost targets 0, allowed 10, added 0", out)
        self.assertNotIn("stale", out)

    def test_outer_class_entry_is_invalid_while_outer_exists(self):
        code, out, _ = self.gate("lib-n | class com/example/n/Outer | 1.0-dataclass | row 1\n")
        self.assertEqual(1, code, out)
        self.assertIn("class entry com.example.n.Outer is invalid", out)

    def jvm_only(self, name, classes):
        """A JVM-only tree with the given class blocks of the nested base dump."""
        blocks = (self.BASE / "lib-n" / "api" / "lib-n.api").read_text(encoding="utf-8").split("\n\n")
        api = self.tmp / name / "lib-n" / "api"
        api.mkdir(parents=True)
        keep = [b for b in blocks if b.split(" {", 1)[0].rsplit("/", 1)[1] in classes]
        self.assertEqual(len(classes), len(keep), name)
        (api / "lib-n.api").write_text("\n\n".join(keep).rstrip("\n") + "\n", encoding="utf-8")
        return self.tmp / name

    def emit_jvm(self, head_classes):
        base = self.jvm_only("base", ["Outer", "Outer$Inner", "Outer$Inner$Deep"])
        head = self.jvm_only("head-" + str(len(list(self.tmp.iterdir()))), head_classes)
        return run_gate(["--baseline-dir", str(base), "--head-dir", str(head), "--baseline-version", "v0.9.2",
                         "--allow-list", str(self.tmp / "none.txt"), "--no-summary", "--emit-removed",
                         "--reason", "1.0-dataclass", "--ledger", "row 1"])

    def test_inner_removed_while_deep_stays_is_gone(self):
        # Asuka QA mutant M10: a nested class left in head does not keep its outer class alive.
        code, out, _ = self.emit_jvm(["Outer", "Outer$Inner$Deep"])
        self.assertEqual(0, code, out)
        self.assertEqual("# lib-n: com.example.n.Outer.Inner\n"
                         "lib-n | class com.example.n.Outer.Inner | 1.0-dataclass | row 1\n", out)

    def test_deep_removed_while_inner_stays_is_gone(self):
        # ...and the reverse: an outer class left in head does not keep its nested class alive.
        code, out, _ = self.emit_jvm(["Outer", "Outer$Inner"])
        self.assertEqual(0, code, out)
        self.assertEqual("# lib-n: com.example.n.Outer.Inner.Deep\n"
                         "lib-n | class com.example.n.Outer.Inner.Deep | 1.0-dataclass | row 1\n", out)

    def test_class_entry_does_not_cover_its_nested_classes(self):
        code, out, _ = self.gate("lib-n | class com/example/n/Outer$Inner | 1.0-dataclass | row 1\n", "--report")
        self.assertEqual(0, code, out)
        self.assertIn("lib-n: removed 4, changed 0, lost targets 0, allowed 6, added 0", out)
        self.assertIn("REMOVED lib-n.api: com.example.n.Outer.Inner.Deep :: public fun <init> ()V", out)


class LedgerMap(unittest.TestCase):
    """--emit-removed --ledger-map: per-line and per-class reason and ledger row (task 8)."""

    LEDGER = ("| B-001 | bridge |\n| S-001 | signature |\n| D-001 | data class |\n| D-002 | data class |\n"
              "| X-001 | defaults duplicate |\n| K-001 | kdoc |\n| `Foo.bar` | covered by B-777 |\n")
    ROWS = [
        ("lib-a", "com.example.a.Foo", "1.0-bridge", "B-001", "public final fun bar ()I"),
        ("lib-a", "com.example.a.Foo", "1.0-signature", "S-001"),
        ("lib-a", "com/example/a/Gone", "1.0-dataclass", "D-001"),
        ("lib-a", "com.example.a.Point", "1.0-dataclass", "D-002", "public final fun component1 ()I"),
    ]

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.head = self.tmp / "head"
        build_head(FIXTURES / "emit", self.head)
        self.empty = self.tmp / "empty.txt"
        self.empty.write_text("", encoding="utf-8")
        self.doc = self.tmp / "ledger.md"
        self.doc.write_text(self.LEDGER, encoding="utf-8")

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def write_map(self, rows):
        path = self.tmp / "map.tsv"
        path.write_text("# comment\n" + "".join("\t".join(r) + "\n" for r in rows), encoding="utf-8")
        return path

    def gate(self, *extra):
        return run_gate(["--baseline-dir", str(BASE), "--head-dir", str(self.head), "--baseline-version",
                         "v0.9.2", "--no-summary", *extra])

    def emit(self, rows, *extra):
        return self.gate("--allow-list", str(self.empty), "--emit-removed", "--ledger-map", str(self.write_map(rows)),
                         *extra)

    @staticmethod
    def entries(text):
        return [l for l in text.splitlines() if l and not l.startswith("#")]

    def test_line_rows_win_over_class_rows_and_unmapped_lines_keep_placeholders(self):
        code, out, err = self.emit(self.ROWS, "--ledger-doc", str(self.doc))
        self.assertEqual(0, code, err)
        self.assertEqual([
            "lib-a | com.example.a.Foo :: final fun baz(kotlin/String) // com.example.a/Foo.baz|baz(kotlin.String){}[0] | 1.0-signature | S-001",
            "lib-a | com.example.a.Foo :: public final fun bar ()I | 1.0-bridge | B-001",
            "lib-a | com.example.a.Foo :: public final fun baz (Ljava/lang/String;)V | 1.0-signature | S-001",
            "lib-a | class com.example.a.Gone | 1.0-dataclass | D-001",
            "lib-a | com.example.a.Point :: public final fun component1 ()I | 1.0-dataclass | D-002",
            "lib-a | com.example.a.Point :: public final fun component2 ()I | TODO-reason | TODO-ledger-row",
        ], self.entries(out))
        self.assertIn("emitted 6 entries, 1 unmapped (TODO)", err)
        self.assertTrue(out.startswith("# Allow-list for tools/api-compat.py"), out)
        self.assertIn("\n# Generated with: python3 tools/api-compat.py --baseline-ref v0.9.2 --emit-removed "
                      "--ledger-map tools/api-ledger-map.tsv\n", out)

    def test_generated_with_line_does_not_echo_the_arguments(self):
        _, first, _ = self.emit(self.ROWS)
        _, second, _ = self.gate("--allow-list", str(self.empty), "--emit-removed", "--ledger-map",
                                 "./" + os.path.relpath(self.write_map(self.ROWS)))
        self.assertEqual(first, second)
        self.assertNotIn(str(self.tmp), first)

    def test_round_trip_with_a_complete_map_passes_the_ledger_check(self):
        rows = self.ROWS + [("lib-a", "com.example.a.Point", "1.0-dataclass", "D-002", "public final fun component2 ()I")]
        _, emitted, _ = self.emit(rows, "--ledger-doc", str(self.doc))
        allow = self.tmp / "allow.txt"
        allow.write_text(emitted, encoding="utf-8")
        code, out, err = self.gate("--allow-list", str(allow), "--ledger-doc", str(self.doc), "--enforce", "true")
        self.assertEqual(0, code, out + err)
        self.assertIn("lib-a: removed 0, changed 0, lost targets 0, allowed 11, added 1", out)
        self.assertNotIn("stale", out)

    def test_class_entry_takes_the_value_its_lines_agree_on(self):
        lines = ["public final class com/example/a/Gone {", "public fun <init> ()V", "public final fun ping ()V",
                 "final class com.example.a/Gone { // com.example.a/Gone|null[0]",
                 "constructor <init>() // com.example.a/Gone.<init>|<init>(){}[0]",
                 "final fun ping() // com.example.a/Gone.ping|ping(){}[0]"]
        rows = [("lib-a", "com.example.a.Gone", "1.0-dataclass", "D-001", l) for l in lines]
        _, out, _ = self.emit(rows)
        self.assertIn("lib-a | class com.example.a.Gone | 1.0-dataclass | D-001", out)
        rows[0] = ("lib-a", "com.example.a.Gone", "1.0-bridge", "B-001", lines[0])
        _, out, _ = self.emit(rows)
        self.assertIn("lib-a | class com.example.a.Gone | TODO-reason | TODO-ledger-row", out)

    def test_partly_mapped_vanished_class_is_a_todo(self):
        # Asuka QA mutant M3: lines without a row must not be outvoted by the mapped ones.
        lines = ["public final class com/example/a/Gone {", "public fun <init> ()V"]
        rows = [r for r in self.ROWS if r[1] != "com/example/a/Gone"]
        rows += [("lib-a", "com.example.a.Point", "1.0-dataclass", "D-002", "public final fun component2 ()I")]
        rows += [("lib-a", "com.example.a.Gone", "1.0-dataclass", "D-001", l) for l in lines]
        _, out, err = self.emit(rows)
        self.assertIn("lib-a | class com.example.a.Gone | TODO-reason | TODO-ledger-row", out)
        self.assertNotIn("lib-a | class com.example.a.Gone | 1.0-dataclass", out)
        self.assertIn("emitted 6 entries, 1 unmapped (TODO)", err)

    def test_class_row_wins_for_a_vanished_class(self):
        # Asuka QA mutant M2: for a class with no declaration left, its class row is the entry.
        rows = [("lib-a", "com.example.a.Gone", "1.0-dataclass", "D-001"),
                ("lib-a", "com.example.a.Gone", "1.0-bridge", "B-001", "public final fun ping ()V")]
        _, out, _ = self.emit(rows)
        self.assertIn("lib-a | class com.example.a.Gone | 1.0-dataclass | D-001", out)

    def test_bad_map_rows_are_setup_errors(self):
        cases = {
            "reason 'removed' is not one of": [("lib-a", "com.example.a.Foo", "removed", "B-001")],
            "is a placeholder": [("lib-a", "com.example.a.Foo", "1.0-bridge", "TODO-ledger-row")],
            "expected '<module>": [("lib-a", "com.example.a.Foo", "1.0-bridge")],
            "a class row for <top-level> is not allowed": [("lib-a", "<top-level>", "1.0-bridge", "B-001")],
            "is mapped twice with different values": [("lib-a", "com.example.a.Foo", "1.0-bridge", "B-001"),
                                                      ("lib-a", "com/example/a/Foo", "1.0-signature", "S-001")],
            "ledger row 'B-999' is not a row ID": [("lib-a", "com.example.a.Foo", "1.0-bridge", "B-999")],
            # An ID that only appears in a later cell of a row is not a row ID.
            "ledger row 'B-777' is not a row ID": [("lib-a", "com.example.a.Foo", "1.0-bridge", "B-777")],
            # The row letter must match the reason (Asuka QA SHOULD 1 on #140).
            "reason 1.0-dataclass must name a row D-NNN of the ledger, not 'K-001'":
                [("lib-a", "com.example.a.Foo", "1.0-dataclass", "K-001")],
            "reason 1.0-bridge must name a row B-NNN of the ledger, not 'S-001'":
                [("lib-a", "com.example.a.Foo", "1.0-bridge", "S-001")],
            "reason 1.0-signature must name a row S-NNN of the ledger, not 'D-001'":
                [("lib-a", "com.example.a.Foo", "1.0-signature", "D-001")],
            # 1.0-removal (X-001, the lead ruling on #140) names an X row, and only it does.
            "reason 1.0-removal must name a row X-NNN of the ledger, not 'B-001'":
                [("lib-a", "com.example.a.Foo", "1.0-removal", "B-001")],
            "reason 1.0-removal must name a row X-NNN of the ledger, not 'S-001'":
                [("lib-a", "com.example.a.Foo", "1.0-removal", "S-001")],
            "reason 1.0-removal must name a row X-NNN of the ledger, not 'D-001'":
                [("lib-a", "com.example.a.Foo", "1.0-removal", "D-001")],
            "reason 1.0-bridge must name a row B-NNN of the ledger, not 'X-001'":
                [("lib-a", "com.example.a.Foo", "1.0-bridge", "X-001")],
            "reason 1.0-signature must name a row S-NNN of the ledger, not 'X-001'":
                [("lib-a", "com.example.a.Foo", "1.0-signature", "X-001")],
            "reason 1.0-dataclass must name a row D-NNN of the ledger, not 'X-001'":
                [("lib-a", "com.example.a.Foo", "1.0-dataclass", "X-001")],
        }
        for message, rows in cases.items():
            code, _, err = self.emit(rows, "--ledger-doc", str(self.doc))
            self.assertEqual(2, code, message)
            self.assertIn(message, err)

    def test_allow_list_ledger_rows_must_be_ledger_ids(self):
        allow = self.tmp / "allow.txt"
        allow.write_text("lib-a | com.example.a.Foo :: public final fun bar ()I | 1.0-bridge | row 1\n", encoding="utf-8")
        code, _, err = self.gate("--allow-list", str(allow), "--ledger-doc", str(self.doc))
        self.assertEqual(2, code)
        self.assertIn("ledger row 'row 1' is not a row ID of the ledger", err)
        allow.write_text("lib-a | com.example.a.Foo :: public final fun bar ()I | 1.0-bridge | D-001\n", encoding="utf-8")
        code, _, err = self.gate("--allow-list", str(allow), "--ledger-doc", str(self.doc))
        self.assertEqual(2, code)
        self.assertIn("reason 1.0-bridge must name a row B-NNN of the ledger, not 'D-001'", err)
        # rc-soak entries carry an approval link, not a row ID.
        allow.write_text("lib-a | com.example.a.Foo :: public final fun bar ()I | rc-soak | "
                         "https://github.com/koza4e4ok/Orrery/issues/89#issuecomment-1\n", encoding="utf-8")
        code, _, err = self.gate("--allow-list", str(allow), "--ledger-doc", str(self.doc), "--report")
        self.assertEqual(0, code, err)

    SOAK = "https://github.com/koza4e4ok/Orrery/issues/89#issuecomment-42"

    def test_rc_soak_map_row_is_generated_and_checked(self):
        # RELEASING step 3: the soak PR adds rc-soak rows (approval URL as the ledger field) to the
        # map and regenerates the allow-list (Ritsuko SHOULD 1 on #140).
        rows = self.ROWS + [("lib-a", "com.example.a.Point", "rc-soak", self.SOAK, "public final fun component2 ()I")]
        code, emitted, err = self.emit(rows, "--ledger-doc", str(self.doc))
        self.assertEqual(0, code, err)
        self.assertIn(f"lib-a | com.example.a.Point :: public final fun component2 ()I | rc-soak | {self.SOAK}",
                      emitted)
        self.assertIn("0 unmapped", err)
        allow = self.tmp / "allow.txt"
        allow.write_text(emitted, encoding="utf-8")
        code, out, err = self.gate("--allow-list", str(allow), "--ledger-doc", str(self.doc), "--enforce", "true")
        self.assertEqual(0, code, out + err)
        self.assertIn("allowed 11", out)

    def test_rc_soak_with_free_text_is_rejected_on_any_baseline(self):
        # Asuka QA SHOULD 2 on #140: a v0.9.2 baseline must not let rc-soak skip the ledger.
        for text in ("because I said so", "https://github.com/koza4e4ok/Orrery/issues/90#issuecomment-1"):
            rows = [("lib-a", "com.example.a.Foo", "rc-soak", text, "public final fun bar ()I")]
            for doc in ([], ["--ledger-doc", str(self.doc)]):
                code, _, err = self.emit(rows, *doc)
                self.assertEqual(2, code, (text, doc))
                self.assertIn("an rc-soak ledger field must be Andrii's approval comment on #89", err)
            allow = self.tmp / "allow.txt"
            allow.write_text(f"lib-a | com.example.a.Foo :: public final fun bar ()I | rc-soak | {text}\n",
                             encoding="utf-8")
            code, _, err = self.gate("--allow-list", str(allow), "--report")
            self.assertEqual(2, code, text)
            self.assertIn("an rc-soak ledger field must be Andrii's approval comment on #89", err)

    def test_removal_rows_name_an_x_row_and_pass_the_gate(self):
        # Task 9: X-001 (CalendarDefaults.DaysOfWeekHeader) is removed at 1.0 with reason 1.0-removal.
        rows = [("lib-a", "com.example.a.Foo", "1.0-removal", "X-001", "public final fun bar ()I")] + self.ROWS[1:]
        rows += [("lib-a", "com.example.a.Point", "1.0-dataclass", "D-002", "public final fun component2 ()I")]
        code, emitted, err = self.emit(rows, "--ledger-doc", str(self.doc))
        self.assertEqual(0, code, err)
        self.assertIn("lib-a | com.example.a.Foo :: public final fun bar ()I | 1.0-removal | X-001", emitted)
        self.assertIn("0 unmapped", err)
        allow = self.tmp / "allow.txt"
        allow.write_text(emitted, encoding="utf-8")
        code, out, err = self.gate("--allow-list", str(allow), "--ledger-doc", str(self.doc), "--report")
        self.assertEqual(0, code, out + err)
        # The allow-list parser applies the same letter rule as the map.
        for row in ("B-001", "S-001", "D-001"):
            allow.write_text(f"lib-a | com.example.a.Foo :: public final fun bar ()I | 1.0-removal | {row}\n",
                             encoding="utf-8")
            code, _, err = self.gate("--allow-list", str(allow), "--ledger-doc", str(self.doc), "--report")
            self.assertEqual(2, code, row)
            self.assertIn(f"reason 1.0-removal must name a row X-NNN of the ledger, not '{row}'", err)
        for reason in ("1.0-bridge", "1.0-signature", "1.0-dataclass"):
            allow.write_text(f"lib-a | com.example.a.Foo :: public final fun bar ()I | {reason} | X-001\n",
                             encoding="utf-8")
            code, _, err = self.gate("--allow-list", str(allow), "--ledger-doc", str(self.doc), "--report")
            self.assertEqual(2, code, reason)
            self.assertIn(f"reason {reason} must name a row", err)
            self.assertIn("not 'X-001'", err)

    def test_row_ids_are_only_read_from_the_first_cell(self):
        ids = api_compat.read_ledger_ids(self.doc)
        self.assertEqual({"B-001", "S-001", "D-001", "D-002", "X-001", "K-001"}, ids)

    def test_missing_ledger_doc_is_a_setup_error(self):
        code, _, err = self.gate("--allow-list", str(self.empty), "--ledger-doc", str(self.tmp / "nope.md"))
        self.assertEqual(2, code)
        self.assertIn("does not exist", err)


class RepositoryLedger(unittest.TestCase):
    """The committed ledger, map and allow-list agree (ci.yml also regenerates the allow-list)."""

    ROOT = HERE.parent

    def test_map_and_allow_list_reference_ledger_rows(self):
        ids = api_compat.read_ledger_ids(self.ROOT / "docs" / "api-audit-1.0.md")
        self.assertTrue(any(i.startswith("B-") for i in ids))
        self.assertTrue(api_compat.parse_ledger_map(self.ROOT / "tools" / "api-ledger-map.tsv", ids))
        self.assertTrue(api_compat.parse_allow_list(self.ROOT / "tools" / "api-compat-allow.txt", ids))
        allow = (self.ROOT / "tools" / "api-compat-allow.txt").read_text(encoding="utf-8")
        self.assertIn("# Generated with: python3 tools/api-compat.py --baseline-ref", allow)


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

    NON_SOAK = "lib-a | com.example.a.Foo :: public final fun bar ()I | 1.0-bridge | B-001\n"

    def add_ledger(self):
        (self.repo / "docs").mkdir(exist_ok=True)
        (self.repo / "docs" / "api-audit-1.0.md").write_text("| B-001 | bridge |\n", encoding="utf-8")

    def test_ledger_doc_is_found_from_the_git_root(self):
        # Asuka QA SHOULD 3 on #140 (mutant M7): run the script itself from the repository, with no
        # --root or --ledger-doc, and a ledger field that is not a row of docs/api-audit-1.0.md.
        self.add_ledger()
        allow = self.repo / "tools" / "api-compat-allow.txt"
        allow.write_text(self.NON_SOAK.replace("B-001", "B-999"), encoding="utf-8")
        proc = subprocess.run([sys.executable, str(HERE / "api-compat.py"), "--baseline-ref", "v0.9.2",
                               "--no-summary"], cwd=self.repo / "tools", capture_output=True, text=True)
        self.assertEqual(2, proc.returncode, proc.stdout + proc.stderr)
        self.assertIn("ledger row 'B-999' is not a row ID of the ledger", proc.stderr)
        allow.write_text(self.NON_SOAK, encoding="utf-8")
        proc = subprocess.run([sys.executable, str(HERE / "api-compat.py"), "--baseline-ref", "v0.9.2",
                               "--no-summary", "--report"], cwd=self.repo / "tools", capture_output=True, text=True)
        self.assertEqual(0, proc.returncode, proc.stdout + proc.stderr)

    def test_allow_list_or_map_without_the_ledger_doc_is_a_setup_error(self):
        (self.repo / "tools" / "api-compat-allow.txt").write_text(self.NON_SOAK, encoding="utf-8")
        code, _, err = self.gate("--report")
        self.assertEqual(2, code)
        self.assertIn("docs/api-audit-1.0.md is missing, but the allow-list names ledger rows", err)
        ledger_map = self.repo / "tools" / "map.tsv"
        ledger_map.write_text("lib-a\tcom.example.a.Foo\t1.0-bridge\tB-001\n", encoding="utf-8")
        code, _, err = self.gate("--emit-removed", "--ledger-map", str(ledger_map))
        self.assertEqual(2, code)
        self.assertIn("docs/api-audit-1.0.md is missing, but the ledger map names ledger rows", err)

    def test_sha_baseline_uses_the_nearest_tag_for_the_rc_rule(self):
        self.add_ledger()
        self.git("tag", "v1.0.0-rc.1", "v0.9.10")
        (self.repo / "tools" / "api-compat-allow.txt").write_text(self.NON_SOAK, encoding="utf-8")
        sha = subprocess.run(["git", "rev-parse", "v0.9.10"], cwd=self.repo, check=True,
                             capture_output=True, text=True).stdout.strip()
        code, out, _ = self.gate("--baseline-ref", sha)
        self.assertEqual(1, code, out)
        self.assertIn(f"baseline {sha} (v1.0.0-rc.1) is 1.0.0-rc.1 or later, so only 'rc-soak'", out)

    def test_rc_tag_baseline_rejects_non_soak_entries(self):
        self.add_ledger()
        self.git("tag", "v1.0.0-rc.1", "v0.9.10")
        (self.repo / "tools" / "api-compat-allow.txt").write_text(self.NON_SOAK, encoding="utf-8")
        code, out, _ = self.gate("--baseline-ref", "v1.0.0-rc.1")
        self.assertEqual(1, code, out)
        self.assertIn("baseline v1.0.0-rc.1 is 1.0.0-rc.1 or later, so only 'rc-soak'", out)

    def test_missing_baseline_ref_is_a_setup_error(self):
        code, _, err = self.gate("--baseline-ref", "v9.9.9")
        self.assertEqual(2, code)
        self.assertIn("git fetch --no-tags --depth=1 origin refs/tags/v9.9.9:refs/tags/v9.9.9", err)


class Workflows(unittest.TestCase):
    """The CI wiring the gate relies on: a strict enforce switch and the publish-gate flags."""

    ROOT = HERE.parent

    def read(self, name):
        return (self.ROOT / ".github" / "workflows" / name).read_text(encoding="utf-8")

    def test_ci_enforce_switch_is_exactly_true_or_false(self):
        ci = self.read("ci.yml")
        values = [line.split(":", 1)[1].split("#", 1)[0].strip()
                  for line in ci.splitlines() if line.strip().startswith("API_COMPAT_ENFORCE:")]
        self.assertEqual(1, len(values), "exactly one API_COMPAT_ENFORCE in ci.yml")
        self.assertIn(values[0], ('"true"', '"false"'))
        # The value goes to argparse choices, which reject anything else (exit 2).
        self.assertIn('--enforce "$API_COMPAT_ENFORCE"', ci)

    def test_publish_forbids_entries_for_final_tags(self):
        publish = self.read("publish.yml")
        self.assertIn('--previous-of "$GITHUB_REF_NAME"', publish)
        self.assertIn("--forbid-entries", publish)


if __name__ == "__main__":
    unittest.main(verbosity=2)
