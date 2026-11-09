#!/usr/bin/env python3
"""Generate the orrery-core RRULE golden fixtures from python-dateutil.

    python3 -m venv .venv && .venv/bin/pip install -r tools/golden/requirements.txt
    .venv/bin/python tools/golden/rrule_fixtures.py

Writes orrery-core/src/jvmTest/resources/golden/rrule-fixtures.txt with one case per line:
`dtstart|rrule|from|to|dates` (dates yyyyMMdd, space separated, the rule's occurrences in the
inclusive window from..to). The corpus is pseudo-random with a fixed seed and only contains
rules Orrery supports. DTSTART is always a date the rule generates, because RFC 5545 makes
DTSTART the first instance even when it does not match the rule while dateutil drops it; that
intentional divergence and the month-end clamp policy are listed, with oracle values, in
golden/rrule-exceptions.csv (rewritten here as well).
python-dateutil (Apache-2.0/BSD) is only a test oracle: nothing from it ships in the library.
"""

import datetime
import pathlib
import random
import signal

import dateutil
from dateutil.rrule import rrulestr

assert dateutil.__version__ == "3.0.0", dateutil.__version__

OUT = pathlib.Path(__file__).resolve().parents[2] / "orrery-core/src/jvmTest/resources/golden"
DAYS = ["MO", "TU", "WE", "TH", "FR", "SA", "SU"]
MONTH_DAYS = [1, 2, 10, 15, 28, 29, 30, 31, -1, -2, -7, -31]
CASES = 220


def fmt(d):
    return d.strftime("%Y%m%d")


def pick(rng, values, lo, hi):
    return sorted(set(rng.sample(values, rng.randint(lo, hi))), key=values.index)


def random_rule(rng):
    freq = rng.choice(["DAILY", "WEEKLY", "MONTHLY", "MONTHLY", "YEARLY", "YEARLY"])
    parts = ["FREQ=" + freq]
    if rng.random() < 0.3:
        parts.append("INTERVAL=%d" % rng.choice([2, 3, 4]))
    by_month = []
    if rng.random() < (0.5 if freq == "YEARLY" else 0.15):
        by_month = sorted(rng.sample(range(1, 13), rng.randint(1, 3)))
        parts.append("BYMONTH=" + ",".join(map(str, by_month)))
    has_month_day = freq != "WEEKLY" and rng.random() < 0.4
    if has_month_day:
        parts.append("BYMONTHDAY=" + ",".join(map(str, sorted(pick(rng, MONTH_DAYS, 1, 2)))))
    has_day = rng.random() < (0.8 if freq == "WEEKLY" else 0.45)
    if has_day:
        days = pick(rng, DAYS, 1, 3)
        if freq == "MONTHLY" or (freq == "YEARLY" and by_month):
            ordinals = [None, None, 1, 2, 3, 4, 5, -1, -2]
        elif freq == "YEARLY":
            ordinals = [None, None, 1, 2, 10, 20, 26, 52, -1, -10]
        else:
            ordinals = [None]
        tokens = []
        for day in days:
            ordinal = rng.choice(ordinals)
            tokens.append(day if ordinal is None else "%d%s" % (ordinal, day))
        parts.append("BYDAY=" + ",".join(tokens))
    if (by_month or has_month_day or has_day) and rng.random() < 0.3:
        parts.append("BYSETPOS=" + ",".join(map(str, sorted(pick(rng, [1, 2, -1, -2, 3], 1, 2)))))
    bound = rng.random()
    if bound < 0.45:
        parts.append("COUNT=%d" % rng.randint(1, 30))
    elif bound < 0.7:
        parts.append("UNTIL=" + fmt(datetime.date(rng.randint(2027, 2032), rng.randint(1, 12), rng.randint(1, 28))))
    if freq == "WEEKLY" and rng.random() < 0.4:
        parts.append("WKST=" + rng.choice(["SU", "WE", "SA"]))
    return ";".join(parts)


class Fruitless(Exception):
    pass


def on_alarm(signum, frame):
    raise Fruitless()


def first_occurrence(rule, seed):
    """First date >= seed, or None when dateutil finds none quickly (fruitless intersections)."""
    signal.signal(signal.SIGALRM, on_alarm)
    signal.alarm(1)
    try:
        return rrulestr(rule, dtstart=seed).after(seed, inc=True)
    except Fruitless:
        return None
    finally:
        signal.alarm(0)


def occurrences(rule, dtstart, start, end):
    r = rrulestr(rule, dtstart=dtstart)
    return [d.date() for d in r.between(start, end, inc=True)]


def main():
    rng = random.Random(86)
    lines = []
    seen = set()
    while len(lines) < CASES * 2:
        rule = random_rule(rng)
        if rule in seen:
            continue
        seed = datetime.datetime(rng.randint(2020, 2027), rng.randint(1, 12), rng.randint(1, 28))
        first = first_occurrence(rule, seed)
        if first is None or first.year > seed.year + 8:
            continue  # fruitless or (almost) empty
        seen.add(rule)
        bounded = "COUNT=" in rule or "UNTIL=" in rule
        full_end = first + datetime.timedelta(days=(3650 if bounded else 730))
        sub_start = first + datetime.timedelta(days=rng.randint(20, 400))
        sub_end = sub_start + datetime.timedelta(days=rng.randint(0, 120))
        for start, end in [(first, full_end), (sub_start, sub_end)]:
            dates = occurrences(rule, first, start, end)
            lines.append("|".join([fmt(first), rule, fmt(start), fmt(end), " ".join(map(fmt, dates))]))
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "rrule-fixtures.txt").write_text("\n".join(lines) + "\n")

    exceptions = [
        # dtstart, rule, ours (Orrery), source
        (
            datetime.datetime(2026, 11, 2),
            "FREQ=WEEKLY;BYDAY=TU;COUNT=3",
            "20261102 20261103 20261110",
            "https://www.rfc-editor.org/rfc/rfc5545#section-3.3.10 (COUNT: DTSTART counts as the first occurrence)",
        ),
        (
            datetime.datetime(2026, 1, 31),
            "FREQ=MONTHLY;COUNT=3",
            "20260131 20260228 20260331",
            "Orrery InvalidDatePolicy.ClampToMonthEnd (non-standard opt-in; RFC 5545 section 3.3.10 skips invalid dates)",
        ),
    ]
    rows = ["dtstart|rrule|ours|oracle|source"]
    for dtstart, rule, ours, source in exceptions:
        oracle = " ".join(fmt(d) for d in rrulestr(rule, dtstart=dtstart))
        rows.append("|".join([fmt(dtstart), rule, ours, oracle, source]))
    (OUT / "rrule-exceptions.csv").write_text("\n".join(rows) + "\n")


if __name__ == "__main__":
    main()
