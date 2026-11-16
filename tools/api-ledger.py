#!/usr/bin/env python3
"""Generates the 1.0 break ledger (docs/api-audit-1.0.md) and its allow-list map
(tools/api-ledger-map.tsv) from the sources and ABI dumps of one git commit (spec 2.4, task 8).

Everything is read from git at `--ref` (default HEAD), so the output is reproducible: rerunning
it at the same commit gives the same files and the same row IDs. The ledger lists:

- B rows: every `@Deprecated(level = HIDDEN)` declaration of the main source sets (the 1.0
  bridges, decision 5), with the PR that added it (git blame of the annotation), the first
  release whose dump has the old signature, and the dump lines that belong to it. A bridge's
  JVM line is the `synthetic` member of the same name and arity (HIDDEN compiles to synthetic),
  plus its `$default` / default-constructor companion; its klib line is the declaration of the
  same name, arity and parameter types.
- S rows: every gate finding against the baseline (`tools/api-compat.py`) that no bridge line
  covers: a signature that changed on main after the baseline tag without a bridge (5C).
- D rows: every public data class (a JVM dump class with `component1` and `copy`), with its
  tier (A/B/C, spec 2.4 table, names resolved to the real declarations) and the generated
  `componentN` surface that tiers A and B lose.
- E and T rows: public enums and sealed types (rule 3), with whether the KDoc already says not
  to rely on an exhaustive `when`.
- L rows: types referenced by a stable JVM signature that no dump declares (an internal,
  `@InternalOrreryApi` or `@ExperimentalOrreryApi` type leaking into stable API, rule 4).
- K rows: KDoc of the stable modules that calls a declaration experimental, internal or
  subject to change.

The map has one tab-separated row per dump line that a B, S or D row removes, in the format of
`tools/api-compat.py --ledger-map`; the allow-list is then generated with
    python3 tools/api-compat.py --baseline-ref v0.9.2 --emit-removed \
        --ledger-map tools/api-ledger-map.tsv > tools/api-compat-allow.txt
Run from the repository root: python3 tools/api-ledger.py [--ref <commit>] [--check].
Needs the full history and the v* tags (git blame, first release). Python standard library only.
"""
import argparse
import importlib.util
import re
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.dont_write_bytecode = True
_spec = importlib.util.spec_from_file_location("api_compat", HERE / "api-compat.py")
api = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(api)

DOC = "docs/api-audit-1.0.md"
MAP = "tools/api-ledger-map.tsv"
BASELINE = "v0.9.2"
STABLE_MODULES = ("orrery-core", "orrery-compose", "orrery-lunar", "orrery-calendars", "orrery-holidays")
EXPERIMENTAL_MODULES = ("orrery-widget",)
MAIN_SOURCE = re.compile(r"^(orrery-[a-z]+)/src/([A-Za-z]+Main)/kotlin/(.+\.kt)$")
DECL = re.compile(r"\b(class|object|interface)\s+(\w+)")
COMPANION = re.compile(r"\bcompanion object\b(?:\s+(\w+))?")
COMPOSER_JVM = "Landroidx/compose/runtime/Composer;"
CTOR_MARKER = "Lkotlin/jvm/internal/DefaultConstructorMarker;"

# Spec 2.4 table. Keys are the real declarations (package-relative, dotted); the third value is
# the spec's name where it differs. Every public data class must be here or in PROPOSED.
TIERS = {
    # Tier A: already needed bridges, proven growth.
    "CalendarEvent": ("A", "event model; already needed bridges (#118 recurrence)", None),
    "DayInfo": ("A", "per-day metadata; already needed bridges", None),
    "SelectionMode.Single": ("A", "selection config; already needed a bridge (allowClear)", None),
    "SelectionMode.Range": ("A", "sibling of SelectionMode.Single, converts with it", None),
    "SelectionMode.Multi": ("A", "sibling of SelectionMode.Single, converts with it", None),
    "SelectionMode.Week": ("A", "sibling of SelectionMode.Single, converts with it", None),
    "SelectionMode.Month": ("A", "sibling of SelectionMode.Single, converts with it", None),
    # Tier B: configuration, state and result types.
    "Selection": ("B", "selection state", None),
    "SelectionResult": ("B", "result type", None),
    "HeatmapSummary": ("B", "result type (aggregates)", None),
    "PositionedEvent": ("B", "layout result", None),
    "DayScheduleLayout": ("B", "layout result", None),
    "AllDayBanner": ("B", "layout result", None),
    "WeekEventBanner": ("B", "layout result", None),
    "WeekBannerPackResult": ("B", "layout result", None),
    "CalendarDay": ("B", "calendar model", None),
    "CalendarWeek": ("B", "calendar model", None),
    "CalendarMonth": ("B", "calendar model", None),
    "VisibleMonthChange": ("B", "observation event (already gained ScrollSource)", None),
    "VisibleWeekChange": ("B", "observation event (already gained ScrollSource)", None),
    "TodayIndicator.Ring": ("B", "style config", "CalendarDefaults.Ring"),
    "CalendarRowHeight.Fixed": ("B", "layout config", "CalendarFormats.Fixed"),
    "CalendarRowHeight.FillViewport": ("B", "layout config", "CalendarFormats.FillViewport"),
    "HolidayRule": ("B", "holiday config", None),
    "PublicHoliday": ("B", "holiday result", None),
    "LunarDayMetadata": ("B", "per-day result", None),
    # Tier C: closed value types, stay data classes.
    "ByDay": ("C", "closed RFC 5545 value (weekday + ordinal)", None),
    "LunarDate": ("C", "closed date value", None),
    "HijriDate": ("C", "closed date value", None),
    "PersianDate": ("C", "closed date value", None),
    "EventSpan.AllDay": ("C", "closed time value", "CalendarEvent.AllDay"),
    "EventSpan.Timed": ("C", "closed time value", "CalendarEvent.Timed"),
}
for _n in ("Fixed", "NthWeekday", "WeekdayRelative", "EasterOffset", "HijriFixed", "PersianFixed", "OneOff"):
    TIERS[f"HolidayDate.{_n}"] = ("C", "closed rule shape", f"HolidayRule variant {_n}")
for _n in ("OutOfRange", "Intercepted", "RangeTooShort", "RangeTooLong", "TooManyDays"):
    TIERS[f"SelectionEvent.{_n}"] = ("C", "closed reason value", f"SelectionResult reason {_n}")
# Not in the spec table: Maya's proposal, accepted or amended with the tier B list.
PROPOSED = {
    "EventOverlapMode.Stacked": ("B", "layout config (one option today, may gain e.g. a lane cap)"),
}


def git(*args):
    proc = subprocess.run(["git", *args], capture_output=True, text=True)
    if proc.returncode != 0:
        raise SystemExit(f"git {' '.join(args)} failed: {proc.stderr.strip()}")
    return proc.stdout


# ---------------------------------------------------------------- parsing helpers


def jvm_params(desc):
    """Parameter types of a JVM method descriptor `(...)R`."""
    inner = desc[desc.index("(") + 1:desc.index(")")]
    out, i = [], 0
    while i < len(inner):
        j = i
        while inner[j] == "[":
            j += 1
        if inner[j] == "L":
            j = inner.index(";", j)
        out.append(inner[i:j + 1])
        i = j + 1
    return out


def split_top(text, sep=","):
    """Splits at `sep` outside (), <>, [] and {} (`->` is not a bracket)."""
    text = text.replace("->", "\u2192")
    out, depth, cur = [], 0, ""
    for ch in text:
        if ch in "(<[{":
            depth += 1
        elif ch in ")>]}":
            depth -= 1
        if ch == sep and depth == 0:
            out.append(cur)
            cur = ""
        else:
            cur += ch
    out.append(cur)
    return [p.strip().replace("\u2192", "->") for p in out if p.strip()]


def balanced(text, start):
    """Index just after the parenthesis that closes the one at `start`."""
    depth = 0
    for i in range(start, len(text)):
        if text[i] == "(":
            depth += 1
        elif text[i] == ")":
            depth -= 1
            if depth == 0:
                return i + 1
    raise ValueError("unbalanced parentheses")


def jvm_member(line):
    """(name without $default, is $default, descriptor) of a JVM dump member line."""
    m = re.search(r"\b(?:fun|field) (\S+) (\(.*)$", line)
    if not m:
        return None, None, None
    full = m.group(1)
    default = full.endswith("$default")
    return (full[:-len("$default")] if default else full), default, m.group(2)


def klib_member(line):
    """(name, receiver or None, parameter types) of a klib dump function or constructor line."""
    decl = line.split(" // ", 1)[0]
    m = re.search(r"\b(?:fun|constructor)\s+(?:<[^>]*>\s*)?(\([^)]*\)\.)?(?:[\w.]+/)?(?:[\w.]+\.)?(<init>|\w+)\s*\(", decl)
    if not m:
        return None, None, None
    open_at = m.end() - 1
    close = balanced(decl, open_at)
    return m.group(2), m.group(1), split_top(decl[open_at + 1:close - 1])


def simple_type(t):
    t = t.split("=")[0].strip()
    t = re.sub(r"<.*>", "", t).rstrip("?").strip()
    return t.split("/")[-1].split(".")[-1]


def types_match(klib_params, source_params):
    """Simple type names of klib and source parameters agree (`Color` vs `.../Color`)."""
    for k, s in zip(klib_params, source_params):
        st = s.split(":", 1)[1] if ":" in s else s
        st = st.split("=")[0].strip()
        st = re.sub(r"^@Composable\s*", "", st)
        if st.startswith("(") or "->" in st:
            if not simple_type(k).startswith("Function"):
                return False
            continue
        a, b = simple_type(k), simple_type(st)
        if a != b and not a.startswith("#"):
            return False
    return True


JVM_WILDCARD_PACKAGES = ("java/", "kotlin/")


def jvm_types_match(jvm_types, source_params):
    """Lenient check that JVM parameter types fit the source ones: an `I` is an `Int`, and a
    class type has the source's simple name unless it is a JDK or Kotlin type (`List`, lambdas);
    other primitives may be value classes (`Color` is `J`, `Dp` is `F`)."""
    for j, s in zip(jvm_types, source_params):
        st = s.split(":", 1)[1] if ":" in s else s
        st = re.sub(r"^@Composable\s*", "", st.split("=")[0].strip())
        lam = st.startswith("(") or "->" in st
        simple = "Function" if lam else simple_type(st)
        if j == "I":
            if simple != "Int":
                return False
        elif j.startswith("L"):
            name = j[1:-1]
            if name.startswith(JVM_WILDCARD_PACKAGES):
                continue
            if name.split("/")[-1].split("$")[-1] != simple:
                return False
        elif simple not in ("Boolean", "Long", "Float", "Double", "Byte", "Short", "Char") and j == "Z":
            return False
    return True


def desynth(line):
    return line.replace(" synthetic ", " ")


def md(text):
    return str(text).replace("|", "\\|")


def short_line(line):
    return line.split(" // ", 1)[0]


# ---------------------------------------------------------------- sources


class Source:
    def __init__(self, ref, path):
        self.path = path
        self.module = MAIN_SOURCE.match(path).group(1)
        self.text = git("show", f"{ref}:{path}")
        self.lines = self.text.splitlines()
        pkg = re.search(r"^package\s+([\w.]+)", self.text, re.M)
        self.package = pkg.group(1) if pkg else ""
        self.facade = Path(path).stem + "Kt"

    @staticmethod
    def indent(line):
        return len(line) - len(line.lstrip(" "))

    def containers(self, index):
        """Enclosing class names of the line at `index`, outermost first (ktlint indentation)."""
        chain, level = [], self.indent(self.lines[index])
        for i in range(index - 1, -1, -1):
            line = self.lines[i]
            if level == 0:
                break
            if not line.strip() or self.indent(line) >= level:
                continue
            stripped = line.strip()
            if stripped[0] in ")}@*/":
                continue
            comp = COMPANION.search(stripped)
            m = DECL.search(stripped)
            if comp:
                chain.insert(0, comp.group(1) or "Companion")
            elif m:
                chain.insert(0, m.group(2))
            else:
                continue
            level = self.indent(line)
        return chain

    def kdoc_before(self, index):
        """The KDoc block directly above the declaration at `index` (annotations skipped)."""
        i = index - 1
        while i >= 0 and self.lines[i].strip().startswith("@"):
            i -= 1
        if i < 0 or not self.lines[i].strip().endswith("*/"):
            return ""
        end = i
        while i >= 0 and "/**" not in self.lines[i]:
            i -= 1
        return "\n".join(self.lines[i:end + 1])


def main_sources(ref):
    paths = git("ls-tree", "-r", "--name-only", ref).splitlines()
    return [Source(ref, p) for p in paths if MAIN_SOURCE.match(p)]


def forwards_to(body):
    if re.match(r"\s*:\s*this\s*\(", body):
        return "primary constructor"
    for m in re.finditer(r"([A-Za-z_][\w.]*)\s*\(", body):
        if m.group(1).split(".")[-1] not in ("if", "when", "remember", "require", "check", "listOf", "setOf"):
            return m.group(1)
    return "?"


class Bridge:
    def __init__(self, src, line_no, header, annotations, message, body):
        self.src, self.line_no, self.module = src, line_no, src.module  # line of @Deprecated
        self.composable = "@Composable" in annotations
        self.message = message
        self.containers = src.containers(line_no - 1)
        if re.search(r"\bconstructor\s*\(", header):
            self.kind, self.receiver, self.name = "constructor", None, "<init>"
            open_at = header.index("(", header.index("constructor"))
        else:
            m = re.search(r"\bfun\s+(?:<[^>]*>\s*)?(?:([\w.]+(?:<[^>]*>)?)\.)?(\w+)\s*\(", header)
            self.kind, self.receiver, self.name = "fun", m.group(1), m.group(2)
            open_at = m.end() - 1
        self.params = split_top(header[open_at + 1:balanced(header, open_at) - 1])
        self.param_names = [p.split(":", 1)[0].split()[-1] for p in self.params]
        tail = header[balanced(header, open_at):]
        self.forwards = "this(...)" if re.search(r":\s*this\s*\(", tail) else forwards_to(body)
        self.jvm, self.klib, self.main_jvm = [], [], []

    @property
    def arity(self):
        return len(self.params) + (1 if self.receiver and not self.containers else 0)

    @property
    def jvm_class(self):
        owner = ".".join(self.containers) if self.containers else self.src.facade
        return api.class_key(f"{self.src.package}.{owner}")

    @property
    def klib_class(self):
        return self.jvm_class if self.containers else api.TOP_LEVEL

    @property
    def display(self):
        owner = ".".join(self.containers)
        if self.kind == "constructor":
            name = owner
        elif self.receiver and not self.containers:
            name = f"{self.receiver}.{self.name}"
        else:
            name = f"{owner}.{self.name}" if owner else self.name
        return f"{name}({', '.join(self.param_names)})"


def find_bridges(sources):
    bridges = []
    for src in sources:
        lines, i = src.lines, 0
        while i < len(lines):
            if not lines[i].strip().startswith("@Deprecated("):
                i += 1
                continue
            start = i
            text = "\n".join(lines[i:i + 12])
            annotation = text[:balanced(text, text.index("("))]
            if "DeprecationLevel.HIDDEN" not in annotation:
                i += 1
                continue
            msg = re.search(r'"((?:[^"\\]|\\.)*)"', annotation)
            i = start + annotation.count("\n") + 1
            annotations = [annotation]
            while lines[i].strip().startswith("@"):
                chunk = "\n".join(lines[i:i + 10])
                ann = chunk[:balanced(chunk, chunk.index("("))] if "(" in lines[i] else lines[i].strip()
                annotations.append(ann.strip())
                i += ann.count("\n") + 1
            rest = "\n".join(lines[i:i + 120])
            open_at = rest.index("(")
            close = balanced(rest, open_at)
            tail = re.match(r"[^{=\n]*", rest[close:]).group(0)
            header = rest[:close] + tail
            body = rest[close + len(tail):close + len(tail) + 600]
            bridges.append(Bridge(src, start + 1, header, " ".join(annotations),
                                  msg.group(1) if msg else "", body.lstrip()))
            i += 1
    return sorted(bridges, key=lambda b: (b.module, b.src.path, b.line_no))


# ---------------------------------------------------------------- dumps


def dump_blocks(text, klib):
    """{class key: [normalised declaration lines]} of one dump."""
    out = {}
    for container, line in api.Dump(text, klib).decls:
        out.setdefault(api.class_key(container), []).append(line)
    return out


def match_bridges(bridges, head):
    """Fills Bridge.jvm and Bridge.klib with the head dump lines that belong to each bridge."""
    problems = []
    for b in bridges:
        jvm, klib, main = set(), set(), set()
        for rel, text in sorted(head.get(b.module, {}).items()):
            is_klib = rel.endswith(".klib.api")
            lines = dump_blocks(text, is_klib).get(b.klib_class if is_klib else b.jvm_class, [])
            hits = []
            if not is_klib:
                for line in lines:
                    name, default, desc = jvm_member(line)
                    if name is None or default or " synthetic " not in f" {line} ":
                        continue
                    params = jvm_params(desc)
                    if name.split("-", 1)[0] != b.name:
                        continue
                    own = params
                    if params and params[-1] == CTOR_MARKER:
                        # Constructors with value-class parameters are all synthetic and take a
                        # marker; other marker lines are default-argument constructors.
                        if b.kind != "constructor":
                            continue
                        own = params[:-1]
                    if b.composable and COMPOSER_JVM in own:
                        own = own[:own.index(COMPOSER_JVM)]
                    if b.receiver and not b.containers:
                        own_types = own[1:]
                    else:
                        own_types = own
                    if len(own) == b.arity and jvm_types_match(own_types, b.params):
                        hits.append((line, name, params))
                if len(hits) != 1:
                    problems.append(f"{b.display} ({b.src.path}:{b.line_no}): {len(hits)} JVM lines in {rel}")
                    continue
                line, name, params = hits[0]
                jvm.add(line)
                main.add(line)
                for other in lines:
                    oname, default, desc = jvm_member(other)
                    if oname is None or other == line:
                        continue
                    op = jvm_params(desc)
                    if default and oname == name and op[-2:] == ["I", "Ljava/lang/Object;"] \
                            and params in (op[:-2], op[1:-2]):
                        jvm.add(other)
                    elif b.kind == "constructor" and oname == "<init>" and \
                            op == [p for p in params if p != CTOR_MARKER] + ["I", CTOR_MARKER]:
                        jvm.add(other)
            else:
                for line in lines:
                    name, receiver, params = klib_member(line)
                    if name != b.name or bool(receiver) != bool(b.receiver and not b.containers):
                        continue
                    own = params
                    for k, p in enumerate(params):
                        if p.startswith("androidx.compose.runtime/Composer"):
                            own = params[:k]
                            break
                    if len(own) == len(b.params) and types_match(own, b.params):
                        hits.append(line)
                if len(hits) != 1:
                    problems.append(f"{b.display} ({b.src.path}:{b.line_no}): {len(hits)} klib lines in {rel}")
                    continue
                klib.update(hits)
        b.jvm, b.klib, b.main_jvm = sorted(jvm), sorted(klib), sorted(main)
    return problems


# ---------------------------------------------------------------- history


def commit_pr(sha):
    subject = git("log", "-1", "--format=%s", sha).strip()
    prs = re.findall(r"\(#(\d+)\)", subject)
    return f"#{prs[-1]}" if prs else sha[:7]


class History:
    def __init__(self, ref, baseline):
        self.ref, self.baseline = ref, baseline
        self.tags = [t for _, t in api.release_tags(Path.cwd())]
        self.cache = {}

    def tag_lines(self, tag):
        if tag not in self.cache:
            lines = set()
            for module, files in api.read_git_dumps(Path.cwd(), tag).items():
                for rel, text in files.items():
                    for _, line in api.Dump(text, rel.endswith(".klib.api")).decls:
                        lines.add((module, desynth(line)))
            self.cache[tag] = lines
        return self.cache[tag]

    def first_release_with(self, module, lines):
        for tag in self.tags:
            if any((module, desynth(l)) in self.tag_lines(tag) for l in lines):
                return tag
        return "unreleased"

    def blame_pr(self, path, line_no):
        out = git("blame", "--porcelain", "-L", f"{line_no},{line_no}", self.ref, "--", path)
        return commit_pr(out.split()[0])

    def pr_of_removal(self, path, line):
        shas = git("log", "--format=%H", "-S", short_line(line), f"{self.baseline}..{self.ref}", "--", path).split()
        return commit_pr(shas[-1]) if shas else "?"


# ---------------------------------------------------------------- inventory


def package_of(cls, sources, module):
    for p in sorted({s.package for s in sources if s.module == module}, key=len, reverse=True):
        if cls.startswith(p + "."):
            return p
    return ""


def data_classes(head, sources):
    rows = []
    for module in STABLE_MODULES:
        files = head.get(module, {})
        jvm = [rel for rel in sorted(files) if not rel.endswith(".klib.api")]
        if not jvm:
            continue
        for cls, lines in sorted(dump_blocks(files[jvm[0]], False).items()):
            names = [(jvm_member(l)[0] or "").split("-", 1)[0] for l in lines]
            comps = [n for n in names if re.fullmatch(r"component\d+", n)]
            if not comps or "copy" not in names:
                continue
            pkg = package_of(cls, sources, module)
            rows.append((module, cls, cls[len(pkg) + 1:] if pkg else cls, len(comps)))
    return rows


def component_lines(module, cls, head):
    out = []
    for rel, text in sorted(head.get(module, {}).items()):
        is_klib = rel.endswith(".klib.api")
        for line in dump_blocks(text, is_klib).get(cls, []):
            name = (klib_member(line)[0] if is_klib else (jvm_member(line)[0] or "").split("-", 1)[0]) or ""
            if re.fullmatch(r"component\d+", name):
                out.append(line)
    return out


def enum_entries(src, index):
    text = "\n".join(src.lines[index:index + 120])
    body = text[text.index("{") + 1:]
    body = re.sub(r"/\*.*?\*/", "", body, flags=re.S)
    body = re.sub(r"//[^\n]*", "", body)
    names, depth, cur = [], 0, ""
    for ch in body:
        if depth == 0 and ch in ";}":
            break
        if ch in "({":
            depth += 1
        elif ch in ")}":
            depth -= 1
        cur += ch
    for part in split_top(cur):
        m = re.match(r"(?:@\w+(?:\([^)]*\))?\s*)*(\w+)", part)
        if m:
            names.append(m.group(1))
    return names


def sealed_subtypes(src, name):
    text = re.sub(r"\([^()]*(?:\([^()]*\)[^()]*)*\)", "()", src.text)
    pat = re.compile(r"public (?:data )?(?:object|class|interface) (\w+)(?:<[^>]*>)?\s*(?:\(\))?\s*:\s*([^{\n]*)")
    return sorted({m.group(1) for m in pat.finditer(text) if re.search(r"\b" + name + r"\b", m.group(2))})


def enums_and_sealed(sources):
    enums, sealed = [], []
    for src in sources:
        for i, line in enumerate(src.lines):
            m = re.match(r"public (enum class|sealed interface|sealed class) (\w+)", line.strip())
            if not m:
                continue
            name = ".".join(src.containers(i) + [m.group(2)])
            note = "yes" if re.search(r"exhaustive", src.kdoc_before(i), re.I) else "no"
            where = f"{Path(src.path).name}:{i + 1}"
            if m.group(1) == "enum class":
                enums.append((src.module, src.package, name, enum_entries(src, i), note, where))
            else:
                sealed.append((src.module, src.package, name, sealed_subtypes(src, m.group(2)), note, where))
    return sorted(enums), sorted(sealed)


def leaks(head):
    declared, refs = set(), {}
    for files in head.values():
        for rel, text in files.items():
            if not rel.endswith(".klib.api"):
                declared.update(re.findall(r"^public .*?class (\S+)", text, re.M))
    for module in STABLE_MODULES:
        for rel, text in sorted(head.get(module, {}).items()):
            if not rel.endswith(".klib.api"):
                for t in re.findall(r"L(me/kozakov/orrery/[\w/$]+);", text):
                    if t not in declared:
                        refs.setdefault(t, set()).add(module)
    return sorted(refs.items())


KDOC_FLAGS = re.compile(r"experimental|internal use|subject to change|unstable|may change", re.I)


def kdoc_flags(sources):
    rows = []
    for src in sources:
        if src.module not in STABLE_MODULES:
            continue
        in_doc = False
        for i, line in enumerate(src.lines):
            s = line.strip()
            if s.startswith("/**"):
                in_doc = True
            if in_doc and KDOC_FLAGS.search(s):
                rows.append((src.module, f"{src.path}:{i + 1}", s.lstrip("/* ").strip()))
            if s.endswith("*/"):
                in_doc = False
    return rows


def finding_name(f):
    cls = api.class_key(f.container)
    short = cls.split(".")[-1]
    if f.file.endswith(".klib.api"):
        m = api.KLIB_SIGNATURE.search(f.line)
        return (m.group(1) if m else f.line).split("/", 1)[-1]
    member = (jvm_member(f.line)[0] or "?").split("-", 1)[0]
    if member == "<init>":
        return short
    return member if short.endswith("Kt") else f"{short}.{member}"


# ---------------------------------------------------------------- generation


def generate(ref, baseline):
    sha = git("rev-parse", "--short", ref).strip()
    sources = main_sources(ref)
    head = api.read_git_dumps(Path.cwd(), ref)
    base = api.read_git_dumps(Path.cwd(), baseline)
    hist = History(ref, baseline)

    bridges = find_bridges(sources)
    problems = match_bridges(bridges, head)
    if problems:
        raise SystemExit("api-ledger: bridge matching failed:\n  " + "\n  ".join(problems))

    map_rows, line_map = [], {}

    def add(module, cls, reason, rid, line):
        key = (module, api.class_key(cls), api.normalise(line))
        if key in line_map:
            if line_map[key] != rid:
                raise SystemExit(f"api-ledger: {key} claimed by {line_map[key]} and {rid}")
            return
        line_map[key] = rid
        map_rows.append((module, api.class_key(cls), reason, rid, api.normalise(line)))

    b_rows = []
    for n, b in enumerate(bridges, 1):
        bid = f"B-{n:03d}"
        for line in b.jvm:
            add(b.module, b.jvm_class, "1.0-bridge", bid, line)
            if line in b.main_jvm and " synthetic " in f" {line} " and CTOR_MARKER not in line:
                add(b.module, b.jvm_class, "1.0-bridge", bid, desynth(line))
        for line in b.klib:
            add(b.module, b.klib_class, "1.0-bridge", bid, line)
        main_lines = b.main_jvm
        in_base = any((b.module, desynth(l)) in hist.tag_lines(baseline) for l in main_lines)
        b_rows.append((bid, b, hist.blame_pr(b.src.path, b.line_no), hist.first_release_with(b.module, main_lines),
                       in_base))

    findings = []
    for module in sorted(set(base) | set(head)):
        if module in EXPERIMENTAL_MODULES:
            continue
        if module not in head:
            raise SystemExit(f"api-ledger: module {module} has no dumps; target loss cannot be ledgered")
        findings += api.compare_module(module, base.get(module, {}), head[module])[0]
    groups = {}
    for f in findings:
        if f.kind == "lost-target":
            raise SystemExit(f"api-ledger: target loss cannot be ledgered: {f.render()}")
        if (f.module, api.class_key(f.container), f.line) not in line_map:
            groups.setdefault((f.module, finding_name(f)), []).append(f)
    s_rows = []
    for n, ((module, name), group) in enumerate(sorted(groups.items()), 1):
        sid = f"S-{n:03d}"
        for f in group:
            add(f.module, f.container, "1.0-signature", sid, f.line)
        pr = hist.pr_of_removal(f"{module}/api/{group[0].file}", group[0].line)
        s_rows.append((sid, module, name, group, pr))

    d_rows = []
    for n, (module, cls, rel_name, comps) in enumerate(data_classes(head, sources), 1):
        did = f"D-{n:03d}"
        if rel_name in TIERS:
            (tier, reason, spec_name), proposed = TIERS[rel_name], False
        elif rel_name in PROPOSED:
            (tier, reason), spec_name, proposed = PROPOSED[rel_name], None, True
        else:
            raise SystemExit(f"api-ledger: data class {rel_name} ({module}) is missing from TIERS/PROPOSED")
        if tier in "AB":
            for line in component_lines(module, cls, head):
                add(module, cls, "1.0-dataclass", did, line)
        d_rows.append((did, module, rel_name, tier, reason, spec_name, proposed, comps))

    enums, sealed = enums_and_sealed(sources)
    doc = render_doc(sha, baseline, b_rows, s_rows, d_rows, enums, sealed, leaks(head), kdoc_flags(sources),
                     findings, line_map)
    return doc, render_map(sha, map_rows)


def render_map(sha, rows):
    out = [
        "# Ledger map for tools/api-compat.py --emit-removed --ledger-map (spec 2.3, 2.4).",
        f"# GENERATED by tools/api-ledger.py --ref {sha}; do not edit by hand.",
        "# <module> TAB <class> TAB <reason> TAB <ledger row of docs/api-audit-1.0.md> TAB <dump line>",
        "# One row per dump line that a ledger row removes: bridges (B), signature changes (S) and",
        "# the componentN functions of the tier A/B data classes (D). A bridge added after v0.9.2",
        "# has two rows, its synthetic head line and the released non-synthetic line. Rows that",
        "# match no finding today are used when task 9 removes the bridges and task 10 converts",
        "# the data classes.",
    ]
    for row in sorted(rows, key=lambda r: (r[3], r[0], r[1], r[4])):
        out.append("\t".join(row))
    return "\n".join(out) + "\n"


def render_doc(sha, baseline, b_rows, s_rows, d_rows, enums, sealed, leak_rows, kdoc_rows, findings, line_map):
    tiers = {t: sum(1 for r in d_rows if r[3] == t) for t in "ABC"}
    by_module = {}
    for _, b, *_ in b_rows:
        by_module[b.module] = by_module.get(b.module, 0) + 1
    mapped = sum(1 for f in findings if (f.module, api.class_key(f.container), f.line) in line_map)
    o = []
    w = o.append
    w("# Orrery 1.0 API audit ledger")
    w("")
    w(f"<!-- GENERATED by tools/api-ledger.py --ref {sha} (baseline {baseline}); do not edit by hand. -->")
    w("")
    w(f"Generated from the sources and ABI dumps of `{sha}` against the baseline `{baseline}` (spec "
      "`docs/superpowers/specs/2026-11-13-v100-phase3-ecosystem-stability-design.md`, 2.3 and 2.4, task 8). "
      "Ritsuko signs the ledger; Andrii accepts or amends the tier B list (decision 8). The row IDs are "
      "fixed for this commit: `tools/api-ledger-map.tsv` and `tools/api-compat-allow.txt` reference them, "
      "and `tools/api-compat.py` rejects a ledger field that is not one of them.")
    w("")
    w("| Section | Rows | Allow-list reason |")
    w("|---|---|---|")
    w(f"| B: HIDDEN bridges, removed at 1.0 (5B) | {len(b_rows)} ("
      + ", ".join(f"`{m}` {c}" for m, c in sorted(by_module.items())) + ") | `1.0-bridge` |")
    w(f"| S: signature changes since `{baseline}` without a bridge (5C) | {len(s_rows)} | `1.0-signature` |")
    w(f"| D: public data classes (8B) | {len(d_rows)} (A {tiers['A']}, B {tiers['B']}, C {tiers['C']}) "
      "| `1.0-dataclass` (A and B) |")
    w(f"| E: public enums (rule 3) | {len(enums)} | - |")
    w(f"| T: public sealed types (rule 3) | {len(sealed)} | - |")
    w(f"| L: stable signatures that reference an undeclared type (rule 4) | {len(leak_rows)} | - |")
    w(f"| K: KDoc that calls stable API experimental, internal or changing | {len(kdoc_rows)} | - |")
    w(f"| Gate findings against `{baseline}` mapped to a row | {mapped} of {len(findings)} | - |")
    w("")
    w("Spec 2.1 counted 43 bridges at `b17cdaa`. #134 (vertical gap between day cells) added two "
      "after that count, so main has the number above; task 9 removes all of them.")
    w("")

    w("## B: HIDDEN bridges (5B, decision 5)")
    w("")
    w("Binary-only break: a recompile against 1.0 needs no source change. *Added in* is the PR of the "
      "`@Deprecated` line (git blame). *First release* is the oldest release whose dump has the bridged "
      f"signature: apps built against it, up to the release before the bridge, link it. *In {baseline}* "
      "says whether the baseline dump has it; a bridge added after the baseline turned a released "
      "signature `synthetic`, which the gate reports as a change today. *Lines* counts the JVM "
      "(android and desktop, or jvm) and klib dump lines of the bridge in the map.")
    w("")
    w(f"| ID | Module | Declaration | Added in | First release | In {baseline} | Forwards to | Deprecation message | Lines (JVM/klib) | Reason |")
    w("|---|---|---|---|---|---|---|---|---|---|")
    for bid, b, pr, first, in_base in b_rows:
        src = f"[{Path(b.src.path).name}:{b.line_no}](../{b.src.path}#L{b.line_no})"
        w(f"| {bid} | `{b.module}` | `{md(b.display)}` {src} | {pr} | {first} | {'yes' if in_base else 'no'} "
          f"| `{md(b.forwards)}` | {md(b.message)} | {len(b.jvm)}/{len(b.klib)} | `1.0-bridge` |")
    w("")

    w(f"## S: signature changes since {baseline} (5C)")
    w("")
    w("A public signature that changed on main after the baseline tag and ships without a bridge "
      "(rule 2: 1.0.0 ships none). Source compatible; a binary-only break like 5B. The list is the "
      "gate's own output: each finding that no bridge line covers belongs to one of these rows. "
      "*Changed in* is the PR that removed the old dump line.")
    w("")
    w("| ID | Module | Declaration | Changed in | Old dump lines | Reason |")
    w("|---|---|---|---|---|---|")
    for sid, module, name, group, pr in s_rows:
        olds = "<br>".join(f"`{f.file}`: `{md(short_line(f.line))}`" for f in group)
        w(f"| {sid} | `{module}` | `{md(name)}` | {pr} | {olds} | `1.0-signature` |")
    w("")

    w("## D: public data classes (8B, decision 8)")
    w("")
    w("Tier A and B become plain classes in task 10 (defaults on the constructor, hand-written `copy`, "
      "`equals`, `hashCode`, `toString`, no `componentN`); tier C stays a `data class`. *Surface "
      "removed* is what the conversion takes out of the dumps: the `componentN` functions, so "
      "destructuring stops compiling. A hand-written `copy` with the same parameters keeps the `copy` "
      "signature. *Spec name* is the name in the spec 2.4 table where it differs from the declaration. "
      "*Proposed* rows are not in the spec table.")
    w("")
    w("| ID | Module | Class | Tier | Reason | Surface removed | Spec name |")
    w("|---|---|---|---|---|---|---|")
    for did, module, name, tier, reason, spec_name, proposed, comps in d_rows:
        surface = (f"`component1`..`component{comps}`" if comps > 1 else "`component1`") if tier in "AB" else "kept"
        w(f"| {did} | `{module}` | `{name}` | **{tier}**{' (proposed)' if proposed else ''} | {md(reason)} "
          f"| {surface} | {md(spec_name or '')} |")
    w("")
    w("### Tier B, for Andrii to accept or amend")
    w("")
    for did, module, name, tier, reason, spec_name, proposed, comps in d_rows:
        if tier == "B":
            w(f"- {did} `{name}` ({module}){': proposed, not in the spec table' if proposed else ''}")
    w("")

    w("## E: public enums (rule 3)")
    w("")
    w("Rule 3: a public enum that may gain entries says \"do not rely on exhaustive `when` across "
      "versions\" in its KDoc. *Note* is whether the KDoc says so today; task 12 adds the missing ones.")
    w("")
    w("| ID | Module | Enum | Entries | Note | Source |")
    w("|---|---|---|---|---|---|")
    for n, (module, pkg, name, entries, note, where) in enumerate(enums, 1):
        w(f"| E-{n:03d} | `{module}` | `{name}` | {', '.join(entries)} | {note} | `{where}` |")
    w("")
    w("## T: public sealed types (rule 3)")
    w("")
    w("| ID | Module | Sealed type | Subtypes | Note | Source |")
    w("|---|---|---|---|---|---|")
    for n, (module, pkg, name, subs, note, where) in enumerate(sealed, 1):
        exp = " (experimental)" if module in EXPERIMENTAL_MODULES else ""
        w(f"| T-{n:03d} | `{module}`{exp} | `{name}` | {', '.join(subs) or '-'} | {note} | `{where}` |")
    w("")
    w("## L: undeclared types in stable signatures (rule 4)")
    w("")
    w("Types under `me/kozakov/orrery/` that a stable JVM dump line references but no dump declares: "
      "an internal type, or one the `@InternalOrreryApi`/`@ExperimentalOrreryApi` dump filters hide.")
    w("")
    if leak_rows:
        w("| ID | Type | Referenced from |")
        w("|---|---|---|")
        for n, (t, modules) in enumerate(leak_rows, 1):
            w(f"| L-{n:03d} | `{t}` | {', '.join(sorted(modules))} |")
    else:
        w("None found.")
    w("")
    w("## K: KDoc wording (2.4 item 3)")
    w("")
    w("KDoc lines of the stable modules that say *experimental*, *internal use*, *subject to change*, "
      "*unstable* or *may change*. Each needs a look: stable API must not call itself any of these.")
    w("")
    if kdoc_rows:
        w("| ID | Module | Where | Text |")
        w("|---|---|---|---|")
        for n, (module, where, text) in enumerate(kdoc_rows, 1):
            w(f"| K-{n:03d} | `{module}` | `{where}` | {md(text)} |")
    else:
        w("None found.")
    w("")
    w(f"## Gate findings against {baseline}")
    w("")
    w(f"Every finding of `tools/api-compat.py` against `{baseline}` at `{sha}` and the row that covers it. "
      "The allow-list is generated from these rows.")
    w("")
    w("| Module | Dump | Kind | Declaration | Row |")
    w("|---|---|---|---|---|")
    for f in sorted(findings, key=lambda f: (f.module, f.file, f.container or "", f.line or "")):
        rid = line_map.get((f.module, api.class_key(f.container), f.line), "**unmapped**")
        w(f"| `{f.module}` | `{f.file}` | {f.kind} | `{md(finding_name(f))}` | {rid} |")
    w("")
    w("## Regenerating")
    w("")
    w("```sh")
    w("git fetch --tags")
    w(f"python3 tools/api-ledger.py --ref {sha}   # this file and tools/api-ledger-map.tsv")
    w(f"python3 tools/api-compat.py --baseline-ref {baseline} --emit-removed \\")
    w("    --ledger-map tools/api-ledger-map.tsv > tools/api-compat-allow.txt")
    w("```")
    w("")
    w("The ledger is fixed at this commit. Once task 9 removes the bridges the generator cannot find "
      "them any more, so later PRs keep this file and the map as they are and regenerate only the "
      "allow-list (CI checks that the committed allow-list is the generated one).")
    return "\n".join(o) + "\n"


def main(argv=None):
    p = argparse.ArgumentParser(description="Generates docs/api-audit-1.0.md and tools/api-ledger-map.tsv.")
    p.add_argument("--ref", default="HEAD", help="commit whose sources and dumps are read (default HEAD)")
    p.add_argument("--baseline-ref", default=BASELINE, help=f"baseline release (default {BASELINE})")
    p.add_argument("--check", action="store_true", help="exit 1 if the committed files differ from the output")
    args = p.parse_args(argv)
    doc, tsv = generate(args.ref, args.baseline_ref)
    if args.check:
        stale = [path for path, text in ((DOC, doc), (MAP, tsv))
                 if not Path(path).exists() or Path(path).read_text(encoding="utf-8") != text]
        if stale:
            print("api-ledger: out of date: " + ", ".join(stale), file=sys.stderr)
            return 1
        print("api-ledger: up to date")
        return 0
    Path(DOC).write_text(doc, encoding="utf-8")
    Path(MAP).write_text(tsv, encoding="utf-8")
    print(f"api-ledger: wrote {DOC} and {MAP}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
