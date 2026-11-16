#!/usr/bin/env python3
"""Checks that the committed ABI dumps only add to the ABI of a baseline release.

`checkKotlinAbi` only proves that the dumps match the code of the same commit, so a change
that deletes a public method and commits the shrunken dump passes it. This script compares
every committed `<module>/api/**/*.api` dump (JVM `*.api` and merged `*.klib.api`) with the
same file at a baseline git ref and fails when the baseline ABI is not a subset of the
current one:

- a baseline declaration line is missing (a removed class or member, or a changed
  signature: the old line is gone and a line with the same name was added);
- a JVM dump file of the baseline is missing (the module lost a JVM target), a klib dump's
  `// Targets: [...]` header lost a target, or a declaration lost a target it had;
- a module of the baseline has no dumps any more.

Additions and new modules pass and are counted (`--verbose` lists them). Comment lines and
whitespace are ignored. The planned 1.0 breaks pass through an allow-list
(`tools/api-compat-allow.txt`, spec 2.3): reasons `1.0-bridge`, `1.0-dataclass`, `1.0-signature`
or `rc-soak`; a `class` entry is valid only for a class with no declaration left in head;
target loss is never allow-listed; stale entries fail in enforce mode. Against a baseline of
`1.0.0-rc.1` or later only `rc-soak` entries linking an approval comment on #89 pass, and a
`1.0.0`+ baseline or `--forbid-entries` (final release tags) takes no entries at all. `--require-empty <module>` fails if that module's dumps declare anything (used for
the experimental `orrery-widget`, whose API the dump filters must hide) and leaves the
module out of the comparison.

Baseline: `--baseline-ref`, else `$API_BASELINE_REF`, else the highest `v*` tag that does
not point at HEAD. `--previous-of vX.Y.Z` picks the highest `v*` tag below that version
(used by the publish workflow). Exit status: 0 compatible (or `--report`), 1 incompatible
or a policy check failed, 2 usage or setup error.

Allow-list lines (`#` starts a comment):
    <module> | class <ClassName> | <reason> | <ledger row>
    <module> | <ClassName> :: <dump line> | <reason> | <ledger row>
Class names may be written in JVM (`a/b/C$D`) or klib (`a.b/C.D`) form; klib top-level
declarations use `<top-level>`. `--emit-removed` prints the missing baseline lines in this
format, grouped by class; a class with no declaration left in head becomes one class entry.

Ledger-driven generation (spec 2.4, task 8): `--emit-removed --ledger-map <tsv>` takes the
reason and ledger row of every entry from the map instead of one `--reason`/`--ledger` for all.
Map rows (tab-separated, `#` starts a comment), the line form wins over the class form:
    <module> <TAB> <class> <TAB> <reason> <TAB> <ledger row>
    <module> <TAB> <class> <TAB> <reason> <TAB> <ledger row> <TAB> <dump line>
A class entry takes its class row, else the one value all its lines map to. Unmapped lines keep
`TODO-reason`/`TODO-ledger-row`, which the allow-list parser rejects. With a map the output
starts with the allow-list header, so `... --emit-removed --ledger-map <tsv> > allow.txt` writes
the whole file. When the ledger `docs/api-audit-1.0.md` exists (or `--ledger-doc` names it),
every non-`rc-soak` ledger field of the allow-list and the map must be a row ID of that file
(`| B-001 |` at the start of a table row). Python standard library only; the selftest is
tools/api-compat-selftest.py, and tools/api-ledger.py generates the ledger and the map.
"""
import argparse
import os
import re
import subprocess
import sys
from pathlib import Path

DUMP_PATH = re.compile(r"^([^/]+)/api/(.+\.api)$")
TARGETS = re.compile(r"^// Targets: \[(.*)\]$")
VERSION = re.compile(r"^v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?$")
JVM_CLASS = re.compile(r"\bclass (\S+)")
JVM_MEMBER = re.compile(r"\b(?:fun|field) (\S+)")
KLIB_SIGNATURE = re.compile(r" // ([^|\s]+)\|")
TOP_LEVEL = "<top-level>"
RC_FLOOR = "1.0.0-rc.1"
FINAL_FLOOR = "1.0.0"
REASONS = ("1.0-bridge", "1.0-dataclass", "1.0-signature", "rc-soak")
PLACEHOLDERS = ("TODO-reason", "TODO-ledger-row")
# rc-soak entries must point at Andrii's approval comment on #89 (spec 2.3, soak rule).
SOAK_LINK = re.compile(r"^https://github\.com/koza4e4ok/Orrery/(issues|pull)/89#issuecomment-[0-9]+$")
DEFAULT_ALLOW_LIST = "tools/api-compat-allow.txt"
DEFAULT_LEDGER_DOC = "docs/api-audit-1.0.md"
# A row ID is the first cell of a ledger table row; an ID anywhere else in a line is not one.
LEDGER_ROW_ID = re.compile(r"^\|\s*([A-Z]-\d{3})\s*\|")
# The ledger field of a 1.0 entry must name a row of the section its reason belongs to.
REASON_ROW_LETTER = {"1.0-bridge": "B", "1.0-signature": "S", "1.0-dataclass": "D"}
ALLOW_HEADER = """\
# Allow-list for tools/api-compat.py: planned 1.0 breaks against the baseline (spec 2.3, 2.4).
# GENERATED, do not edit by hand. Regenerate with the command below; Ritsuko reviews the result.
#
# One entry per line, either a whole class or one dump line of a class:
#   <module> | class <ClassName> | <reason> | <ledger row>
#   <module> | <ClassName> :: <dump line> | <reason> | <ledger row>
# Reason is one of: 1.0-bridge, 1.0-dataclass, 1.0-signature, rc-soak. Anything else, or a
# TODO-reason / TODO-ledger-row placeholder, is an error (exit 2). The ledger row is a row ID of
# docs/api-audit-1.0.md; the reason and row of each entry come from tools/api-ledger-map.tsv.
# A class entry is valid only if the class has no declaration left in any head dump of the
# module; "class <top-level>" is rejected. Target loss is never allow-listable. An entry that
# matches nothing is a warning in report mode and fails in enforce mode.
#
# Lifecycle: used while the baseline is v0.9.2 (PR gate and the publish gate of the
# v1.0.0-rc.1 tag). The first PR after v1.0.0-rc.1 is tagged moves API_BASELINE_REF in
# ci.yml to the RC and empties this file. From an rc.1 baseline on, only rc-soak entries
# whose ledger field is Andrii's approval comment on #89
# (https://github.com/koza4e4ok/Orrery/issues/89#issuecomment-<id>) pass; a final tag
# (publish.yml passes --forbid-entries) and any 1.0.0+ baseline take no entries at all.
"""
KINDS = ("removed", "changed", "lost-target")


class SetupError(Exception):
    pass


# ---------------------------------------------------------------- versions


def parse_version(text):
    """Returns a sortable key for `vX.Y.Z[-pre]` following SemVer precedence, or None."""
    m = VERSION.match(text.strip())
    if not m:
        return None
    major, minor, patch, pre = m.groups()
    ids = ()
    if pre:
        ids = tuple((0, int(p), "") if p.isdigit() else (1, 0, p) for p in pre.split("."))
    # A release sorts after all of its pre-releases.
    return (int(major), int(minor), int(patch), 0 if pre else 1, ids)


# ---------------------------------------------------------------- dumps


def normalise(line):
    return " ".join(line.split())


def class_key(name):
    """`a/b/C$D` (JVM) and `a.b/C.D` (klib) both become `a.b.C.D`."""
    return re.sub(r"[/$]", ".", name.strip())


class Dump:
    """Declarations of one dump file: {(container, line): targets, or None for all targets}."""

    def __init__(self, text, klib):
        self.klib = klib
        self.targets = None
        self.decls = {}
        stack = []  # (container, targets) of the open class blocks
        pending = None
        seen_decl = False
        for raw in text.splitlines():
            line = normalise(raw)
            if not line:
                continue
            if line.startswith("//"):
                m = TARGETS.match(line)
                if klib and m:
                    targets = frozenset(t.strip() for t in m.group(1).split(",") if t.strip())
                    if self.targets is None and not seen_decl:
                        self.targets = targets
                    else:
                        pending = targets  # applies to the next declaration only
                continue
            if line == "}":
                if stack:
                    stack.pop()
                continue
            seen_decl = True
            opens = line.split(" // ", 1)[0].endswith("{")
            targets = pending if pending is not None else (stack[-1][1] if stack else None)
            pending = None
            if opens:
                container = self._class_name(line)
            else:
                container = stack[-1][0] if stack else TOP_LEVEL
            self.decls[(container, line)] = targets
            if opens:
                stack.append((container, targets))

    def _class_name(self, line):
        m = (KLIB_SIGNATURE if self.klib else JVM_CLASS).search(line)
        return class_key(m.group(1)) if m else class_key(line)

    def identity(self, container, line):
        """What a declaration is called, so a removed line whose name was re-added is a change."""
        if self.klib:
            m = KLIB_SIGNATURE.search(line)
            return (container, m.group(1) if m else line)
        if line.endswith("{"):
            return (container, "<class>")
        m = JVM_MEMBER.search(line)
        # Drop the hash suffix Kotlin adds for inline-class parameters (`dayShapes-Z4HSEVQ`).
        return (container, m.group(1).split("-", 1)[0] if m else line)

    def effective_targets(self, key):
        targets = self.decls[key]
        return targets if targets is not None else (self.targets or frozenset())


def read_tree_dumps(root):
    """{module: {path under api/: text}} from a directory laid out like the repository."""
    result = {}
    for module_dir in sorted(p for p in Path(root).iterdir() if p.is_dir()):
        api = module_dir / "api"
        if not api.is_dir():
            continue
        for path in sorted(api.rglob("*.api")):
            rel = path.relative_to(api).as_posix()
            result.setdefault(module_dir.name, {})[rel] = path.read_text(encoding="utf-8")
    return result


def git(root, *args):
    proc = subprocess.run(["git", *args], cwd=root, capture_output=True, text=True)
    if proc.returncode != 0:
        raise SetupError(f"git {' '.join(args)} failed: {proc.stderr.strip()}")
    return proc.stdout


def read_git_dumps(root, ref):
    try:
        git(root, "rev-parse", "--verify", "--quiet", f"{ref}^{{commit}}")
    except SetupError:
        raise SetupError(
            f"baseline ref {ref!r} is not available; fetch it first, e.g. "
            f"git fetch --no-tags --depth=1 origin refs/tags/{ref}:refs/tags/{ref}"
        ) from None
    result = {}
    for path in git(root, "ls-tree", "-r", "--name-only", ref).splitlines():
        m = DUMP_PATH.match(path)
        if m:
            result.setdefault(m.group(1), {})[m.group(2)] = git(root, "show", f"{ref}:{path}")
    return result


# ---------------------------------------------------------------- baseline ref


def release_tags(root):
    tags = []
    for tag in git(root, "tag", "--list", "v*").split():
        key = parse_version(tag)
        if key:
            tags.append((key, tag))
    return sorted(tags)


def resolve_baseline(root, args):
    if args.baseline_ref:
        return args.baseline_ref
    if args.previous_of:
        floor = parse_version(args.previous_of)
        if not floor:
            raise SetupError(f"--previous-of {args.previous_of!r} is not a vX.Y.Z tag")
        older = [tag for key, tag in release_tags(root) if key < floor]
        if not older:
            raise SetupError(f"no v* tag below {args.previous_of}")
        return older[-1]
    env = os.environ.get("API_BASELINE_REF", "").strip()
    if env:
        return env
    at_head = set(git(root, "tag", "--points-at", "HEAD").split())
    candidates = [tag for _, tag in release_tags(root) if tag not in at_head]
    if not candidates:
        raise SetupError("no v* tag found; set API_BASELINE_REF or pass --baseline-ref")
    return candidates[-1]


def baseline_version(root, ref):
    """The release a baseline ref stands for: its own name, else the highest v* tag it contains."""
    if parse_version(ref):
        return ref
    tags = [(parse_version(t), t) for t in git(root, "tag", "--merged", ref, "--list", "v*").split()]
    tags = sorted((key, t) for key, t in tags if key)
    return tags[-1][1] if tags else None


# ---------------------------------------------------------------- allow-list


def head_classes(head_dumps):
    """{module: set of class keys that still have a declaration in any head dump of the module}."""
    result = {}
    for module, files in head_dumps.items():
        names = result.setdefault(module, set())
        for rel, text in files.items():
            names.update(class_key(c) for c, _ in Dump(text, rel.endswith(".klib.api")).decls)
    return result


def class_gone(classes, module, cls):
    """True if `cls` has no declaration left in any head dump (JVM and klib) of `module`.

    The one predicate for both `--emit-removed` (when to write a `class` entry) and the check
    (when a `class` entry is valid), so generating and checking cannot diverge.
    """
    return cls != TOP_LEVEL and class_key(cls) not in classes.get(module, set())


class AllowEntry:
    def __init__(self, module, cls, line, reason, ledger, where):
        self.module, self.cls, self.line, self.where = module, cls, line, where
        self.reason, self.ledger = reason, ledger
        self.used = False
        self.invalid = False

    def matches(self, finding):
        if self.invalid or finding.kind == "lost-target":
            return False  # target loss is never allow-listable (spec 2.3)
        if finding.module != self.module or class_key(finding.container) != self.cls:
            return False
        return self.line is None or self.line == finding.line


def read_ledger_ids(path):
    """Row IDs (`B-001`, ...) of the ledger file, or None if there is no ledger to check against."""
    if path is None:
        return None
    if not Path(path).exists():
        raise SetupError(f"ledger {path} does not exist")
    ids = set()
    for raw in Path(path).read_text(encoding="utf-8").splitlines():
        m = LEDGER_ROW_ID.match(raw.strip())
        if m:
            ids.add(m.group(1))
    return ids


def check_ledger_field(where, reason, ledger, ledger_ids):
    if reason not in REASONS:
        raise SetupError(f"{where}: reason {reason!r} is not one of {', '.join(REASONS)}")
    if ledger in PLACEHOLDERS:
        raise SetupError(f"{where}: ledger field {ledger!r} is a placeholder; fill in the ledger row")
    if ledger_ids is not None and reason != "rc-soak":
        if ledger not in ledger_ids:
            raise SetupError(f"{where}: ledger row {ledger!r} is not a row ID of the ledger "
                             f"({DEFAULT_LEDGER_DOC}, rows like '| B-001 |')")
        letter = REASON_ROW_LETTER[reason]
        if not re.fullmatch(rf"{letter}-\d{{3}}", ledger):
            raise SetupError(f"{where}: reason {reason} must name a row {letter}-NNN of the ledger, "
                             f"not {ledger!r}")


def parse_ledger_map(path, ledger_ids):
    """{(module, class key, line or None): (reason, ledger)} from the tab-separated ledger map."""
    rows = {}
    if not Path(path).exists():
        raise SetupError(f"ledger map {path} does not exist")
    for n, raw in enumerate(Path(path).read_text(encoding="utf-8").splitlines(), 1):
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        where = f"{path}:{n}"
        fields = [f.strip() for f in raw.split("\t")]
        if len(fields) not in (4, 5) or not all(fields):
            raise SetupError(f"{where}: expected '<module>\\t<class>\\t<reason>\\t<ledger row>[\\t<dump line>]'")
        module, cls, reason, ledger = fields[:4]
        check_ledger_field(where, reason, ledger, ledger_ids)
        key = (module, class_key(cls), normalise(fields[4]) if len(fields) == 5 else None)
        if key[2] is None and key[1] == TOP_LEVEL:
            raise SetupError(f"{where}: a class row for {TOP_LEVEL} is not allowed; map its lines one by one")
        if key in rows and rows[key] != (reason, ledger):
            raise SetupError(f"{where}: {key[1]} {key[2] or '(class row)'} is mapped twice with different values")
        rows[key] = (reason, ledger)
    return rows


def parse_allow_list(path, ledger_ids=None):
    entries = []
    if path is None or not Path(path).exists():
        return entries
    for n, raw in enumerate(Path(path).read_text(encoding="utf-8").splitlines(), 1):
        text = raw.strip()
        if not text or text.startswith("#"):
            continue
        where = f"{path}:{n}"
        # Dump lines contain "|" (klib signatures) but never " | ".
        head = text.split(" | ", 1)
        tail = head[1].rsplit(" | ", 2) if len(head) == 2 else []
        fields = [head[0].strip()] + [t.strip() for t in tail]
        if len(fields) != 4 or not all(fields):
            raise SetupError(f"{where}: expected '<module> | <class Name or Name :: dump line> | <reason> | <ledger row>'")
        module, target, reason, ledger = fields
        check_ledger_field(where, reason, ledger, ledger_ids)
        if target.startswith("class "):
            cls = class_key(target[6:])
            if cls == TOP_LEVEL:
                raise SetupError(f"{where}: 'class {TOP_LEVEL}' is not allowed; list top-level lines one by one")
            entries.append(AllowEntry(module, cls, None, reason, ledger, where))
        elif " :: " in target:
            cls, line = target.split(" :: ", 1)
            entries.append(AllowEntry(module, class_key(cls), normalise(line), reason, ledger, where))
        else:
            raise SetupError(f"{where}: the second field must be 'class <Name>' or '<Name> :: <dump line>'")
    return entries


# ---------------------------------------------------------------- comparison


class Finding:
    def __init__(self, module, file, kind, container, line, detail=""):
        self.module, self.file, self.kind = module, file, kind
        self.container, self.line, self.detail = container, line, detail
        self.allowed_by = None

    def render(self):
        text = f"{self.kind.upper()} {self.file}: "
        if self.line is None:
            return text + self.detail
        return text + f"{self.container} :: {self.line}" + (f" ({self.detail})" if self.detail else "")


def compare_module(module, base_files, head_files):
    findings, added = [], []
    for rel, base_text in sorted(base_files.items()):
        klib = rel.endswith(".klib.api")
        base = Dump(base_text, klib)
        if rel not in head_files:
            findings.append(Finding(module, rel, "lost-target", None, None,
                                    "dump file is gone: the module lost this target"))
            continue
        head = Dump(head_files[rel], klib)
        lost_header = frozenset()
        if klib and base.targets:
            lost_header = base.targets - (head.targets or frozenset())
            if lost_header:
                findings.append(Finding(module, rel, "lost-target", None, None,
                                        f"klib targets lost: {', '.join(sorted(lost_header))}"))
        added_ids = {head.identity(*key) for key in head.decls if key not in base.decls}
        for key in base.decls:
            if key not in head.decls:
                kind = "changed" if base.identity(*key) in added_ids else "removed"
                findings.append(Finding(module, rel, kind, *key))
            elif klib:
                lost = base.effective_targets(key) - head.effective_targets(key) - lost_header
                if lost:
                    findings.append(Finding(module, rel, "lost-target", *key,
                                            f"no longer on {', '.join(sorted(lost))}"))
        added += [(rel, c, l) for (c, l) in head.decls if (c, l) not in base.decls]
    for rel, text in sorted(head_files.items()):
        if rel not in base_files:
            added += [(rel, c, l) for (c, l) in Dump(text, rel.endswith(".klib.api")).decls]
    return findings, added


def emit_removed(findings, classes, reason, ledger, ledger_map=None):
    """Allow-list lines for every missing baseline line; one class entry per vanished class.

    Without a ledger map every entry gets `reason` and `ledger`; with one, each line takes its
    line row, else its class row, else the defaults (placeholders unless given). Returns the
    lines and the number of entries left on the defaults.
    """
    ledger_map = ledger_map or {}
    default = (reason, ledger)
    out, unmapped = [], 0
    missing = {}
    for f in findings:
        if f.kind in ("removed", "changed"):
            missing.setdefault(f.module, {}).setdefault(class_key(f.container), set()).add(f.line)

    def value(module, cls, line):
        return ledger_map.get((module, cls, line)) or ledger_map.get((module, cls, None))

    for module in sorted(missing):
        for cls in sorted(missing[module]):
            gone = missing[module][cls]
            out.append(f"# {module}: {cls}")
            if class_gone(classes, module, cls):
                found = ledger_map.get((module, cls, None))
                if found is None:
                    values = {value(module, cls, line) for line in gone}
                    found = values.pop() if len(values) == 1 else None
                unmapped += found is None
                r, l = found or default
                out.append(f"{module} | class {cls} | {r} | {l}")
            else:
                for line in sorted(gone):
                    found = value(module, cls, line)
                    unmapped += found is None
                    r, l = found or default
                    out.append(f"{module} | {cls} :: {line} | {r} | {l}")
    return out, unmapped


def run(args):
    out = []
    root = None
    if not (args.baseline_dir and args.head_dir):
        root = args.root or git(Path.cwd(), "rev-parse", "--show-toplevel").strip()
    head_dumps = read_tree_dumps(args.head_dir or root)

    if args.baseline_dir:
        ref, version = args.baseline_dir, args.baseline_version
        base_dumps = read_tree_dumps(args.baseline_dir)
    else:
        ref = resolve_baseline(root, args)
        version = args.baseline_version or baseline_version(root, ref)
        base_dumps = read_git_dumps(root, ref)

    allow_path = args.allow_list
    if allow_path is None and root is not None:
        allow_path = os.path.relpath(Path(root) / DEFAULT_ALLOW_LIST)
    ledger_doc = args.ledger_doc
    if ledger_doc is None and root is not None and (Path(root) / DEFAULT_LEDGER_DOC).exists():
        ledger_doc = os.path.relpath(Path(root) / DEFAULT_LEDGER_DOC)
    ledger_ids = read_ledger_ids(ledger_doc)
    ledger_map = parse_ledger_map(args.ledger_map, ledger_ids) if args.ledger_map else None
    entries = [] if args.emit_removed else parse_allow_list(allow_path, ledger_ids)

    report = args.report or args.enforce == "false"
    label = ref if not version or version == ref else f"{ref} ({version})"
    failures = []  # policy checks: they fail in report mode too
    version_key = parse_version(version) if version else None
    if args.forbid_entries and entries:
        failures.append(f"--forbid-entries: a final release takes no allow-list entries "
                        f"({len(entries)} in {allow_path})")
    elif version_key and version_key >= parse_version(FINAL_FLOOR) and entries:
        failures.append(f"baseline {label} is {FINAL_FLOOR} or later, so the allow-list must be empty "
                        f"({len(entries)} entries in {allow_path})")
    elif version_key and version_key >= parse_version(RC_FLOOR):
        for e in entries:
            if e.reason != "rc-soak" or not SOAK_LINK.match(e.ledger):
                failures.append(f"{e.where}: baseline {label} is {RC_FLOOR} or later, so only 'rc-soak' "
                                f"entries whose ledger field links Andrii's approval comment on #89 "
                                f"(https://github.com/koza4e4ok/Orrery/issues/89#issuecomment-<id>) are allowed")
    if not version_key:
        message = (f"baseline {ref} has no v* release version, so the allow-list rules for "
                   f"{RC_FLOOR}+ baselines cannot be applied")
        if report:
            out.append(f"note: {message}")
        else:
            failures.append(f"{message}; use a v* tag or a commit that contains one")

    for module in args.require_empty:
        files = head_dumps.get(module)
        if not files:
            failures.append(f"--require-empty {module}: the module has no committed dumps")
            continue
        for rel, text in sorted(files.items()):
            decls = list(Dump(text, rel.endswith(".klib.api")).decls)
            if decls:
                shown = "\n".join(f"    {line}" for _, line in decls[:5])
                failures.append(f"--require-empty {module}: api/{rel} declares {len(decls)} line(s); "
                                f"mark them @ExperimentalOrreryApi or @InternalOrreryApi:\n{shown}")

    modules = sorted(set(base_dumps) | set(head_dumps))
    findings, additions = [], {}
    for module in modules:
        if module in args.require_empty:
            continue
        if module not in head_dumps:
            findings.append(Finding(module, "api/", "lost-target", None, None,
                                    "the module has no dumps any more (removed module)"))
            continue
        module_findings, added = compare_module(module, base_dumps.get(module, {}), head_dumps[module])
        findings += module_findings
        additions[module] = added

    classes = head_classes(head_dumps)
    if args.emit_removed:
        lines, unmapped = emit_removed(findings, classes, args.reason, args.ledger, ledger_map)
        if ledger_map is not None:
            command = f"python3 tools/api-compat.py --baseline-ref {ref} --emit-removed --ledger-map {args.ledger_map}"
            lines = [*ALLOW_HEADER.splitlines(), f"#", f"# Generated with: {command}", "", *lines]
        print("\n".join(lines))
        count = sum(1 for line in lines if line and not line.startswith("#"))
        print(f"api-compat: emitted {count} entries, {unmapped} unmapped (TODO)", file=sys.stderr)
        return 0

    for e in entries:
        if e.line is None and not class_gone(classes, e.module, e.cls):
            e.invalid = True
            failures.append(f"{e.where}: class entry {e.cls} is invalid: the class still exists in head; "
                            f"list the lines instead (--emit-removed)")
    for f in findings:
        for entry in entries:
            if entry.matches(f):
                entry.used = True
                f.allowed_by = entry
                break
    blocking = [f for f in findings if f.allowed_by is None]

    mode = "report" if report else "enforce"
    shown_ref = ref + (f" ({version})" if version and version != ref else "")
    out.insert(0, f"api-compat: baseline {shown_ref}, allow-list {allow_path or '-'} "
                  f"({len(entries)} entries), mode {mode}")
    rows = []
    for module in modules:
        if module in args.require_empty:
            out.append(f"{module}: required to be empty, not compared")
            continue
        mine = [f for f in findings if f.module == module]
        counts = {k: sum(1 for f in mine if f.kind == k and f.allowed_by is None) for k in KINDS}
        allowed = sum(1 for f in mine if f.allowed_by is not None)
        label = module + ("" if module in base_dumps else " (new module)")
        n_added = len(additions.get(module, []))
        rows.append((module, label, counts, allowed, n_added))
        out.append(f"{label}: removed {counts['removed']}, changed {counts['changed']}, "
                   f"lost targets {counts['lost-target']}, allowed {allowed}, added {n_added}")
        out += [f"  {f.render()}" for f in mine if f.allowed_by is None]
        if args.verbose:
            out += [f"  ALLOWED {f.render()} [{f.allowed_by.where}]" for f in mine if f.allowed_by]
            out += [f"  ADDED {rel}: {c} :: {l}" for rel, c, l in additions.get(module, [])]
    for e in entries:
        if not e.used and not e.invalid:
            stale = f"{e.where}: allow-list entry matches nothing (stale)"
            if report:
                out.append(f"warning: {stale}")
            else:
                failures.append(stale)
    out += [f"FAILED: {msg}" for msg in failures]
    if blocking:
        verdict = f"{len(blocking)} incompatible change(s) against {ref}"
        verdict = ("REPORT: " + verdict + " (report mode: not enforced)") if report else ("INCOMPATIBLE: " + verdict)
        if report and os.environ.get("GITHUB_ACTIONS") == "true":
            out.append(f"::warning::api-compat: {len(blocking)} incompatible change(s) against {ref} "
                       f"(report mode, see the job summary)")
    else:
        verdict = f"OK: compatible with {ref}"
    out.append(verdict)
    print("\n".join(out))

    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary and not args.no_summary:
        write_summary(summary, shown_ref, mode, rows, findings, additions, failures, verdict)
    return 1 if failures or (blocking and not report) else 0


def write_summary(path, ref, mode, rows, findings, additions, failures, verdict):
    md = [f"### ABI compatibility against {ref} ({mode} mode)", "", verdict, "",
          "| Module | Removed | Changed | Lost targets | Allowed | Added |", "|---|---|---|---|---|---|"]
    for _, label, counts, allowed, n_added in rows:
        md.append(f"| {label} | {counts['removed']} | {counts['changed']} | {counts['lost-target']} "
                  f"| {allowed} | {n_added} |")
    md += ["", *(f"**FAILED:** {msg}" for msg in failures)]
    for module, *_ in rows:
        lines = [f.render() for f in findings if f.module == module and f.allowed_by is None]
        lines += [f"ADDED {rel}: {c} :: {l}" for rel, c, l in additions.get(module, [])]
        if lines:
            shown = lines[:300] + ([f"... {len(lines) - 300} more"] if len(lines) > 300 else [])
            md += ["", f"<details><summary>{module}</summary>", "", "~~~", *shown, "~~~", "</details>"]
    with open(path, "a", encoding="utf-8") as fh:
        fh.write("\n".join(md) + "\n")


def main(argv=None):
    p = argparse.ArgumentParser(description="Fails when the committed ABI dumps drop or change "
                                "anything of a baseline release. See the module docstring.")
    p.add_argument("--baseline-ref", help="git ref of the baseline (default: $API_BASELINE_REF, "
                   "else the highest v* tag not at HEAD)")
    p.add_argument("--previous-of", metavar="TAG", help="use the highest v* tag below TAG as the baseline")
    p.add_argument("--allow-list", help=f"allow-list file (default: {DEFAULT_ALLOW_LIST})")
    p.add_argument("--require-empty", action="append", default=[], metavar="MODULE",
                   help="fail if MODULE's dumps declare anything; MODULE is not compared")
    p.add_argument("--report", action="store_true",
                   help="print incompatibilities but exit 0 (policy checks still fail)")
    p.add_argument("--enforce", choices=("true", "false"),
                   help="CI switch (API_COMPAT_ENFORCE): 'false' is --report; any other value is an error")
    p.add_argument("--forbid-entries", action="store_true",
                   help="fail if the allow-list has any entry (publish gate of a final release tag)")
    p.add_argument("--emit-removed", action="store_true",
                   help="print the missing baseline lines as allow-list entries and exit 0")
    p.add_argument("--reason", default="TODO-reason", help="reason field for --emit-removed")
    p.add_argument("--ledger", default="TODO-ledger-row", help="ledger field for --emit-removed")
    p.add_argument("--ledger-map", metavar="TSV", help="per-class and per-line reason and ledger row "
                   "for --emit-removed (tools/api-ledger-map.tsv); unmapped lines keep the defaults")
    p.add_argument("--ledger-doc", help=f"ledger whose row IDs the ledger fields must name "
                   f"(default: {DEFAULT_LEDGER_DOC} if it exists)")
    p.add_argument("--verbose", action="store_true", help="also list additions and allowed lines")
    p.add_argument("--root", help="repository root (default: git toplevel of the cwd)")
    p.add_argument("--baseline-dir", help=argparse.SUPPRESS)
    p.add_argument("--head-dir", help=argparse.SUPPRESS)
    p.add_argument("--baseline-version", help=argparse.SUPPRESS)
    p.add_argument("--no-summary", action="store_true", help=argparse.SUPPRESS)
    args = p.parse_args(argv)
    try:
        return run(args)
    except SetupError as e:
        print(f"api-compat: error: {e}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
