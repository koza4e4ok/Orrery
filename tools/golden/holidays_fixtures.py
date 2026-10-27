#!/usr/bin/env python3
"""Generate the orrery-holidays golden fixtures from python-holidays.

    python3 -m venv .venv && .venv/bin/pip install -r tools/golden/requirements.txt
    .venv/bin/python tools/golden/holidays_fixtures.py

Writes orrery-holidays/src/jvmTest/resources/golden/holidays-<cc>.csv with rows
`date,subdivision,ruleId,substitute` (subdivision empty = nationwide, substitute 0/1).
python-holidays (MIT) is only a test oracle: nothing from it ships in the library. Its
holiday names are mapped to Orrery rule ids below; an unknown name fails the script.
Known, sourced disagreements live in golden/holidays-exceptions.csv, not here.
"""

import csv
import pathlib

import holidays

assert holidays.__version__ == "0.107", holidays.__version__

OUT = pathlib.Path(__file__).resolve().parents[2] / "orrery-holidays/src/jvmTest/resources/golden"

US = {
    "New Year's Day": "us.new-years-day",
    "Martin Luther King Jr. Day": "us.martin-luther-king-day",
    "Washington's Birthday": "us.washingtons-birthday",
    "Memorial Day": "us.memorial-day",
    "Juneteenth National Independence Day": "us.juneteenth",
    "Independence Day": "us.independence-day",
    "Labor Day": "us.labor-day",
    "Columbus Day": "us.columbus-day",
    "Veterans Day": "us.veterans-day",
    "Thanksgiving Day": "us.thanksgiving-day",
    "Christmas Day": "us.christmas-day",
}

GB = {
    "New Year's Day": "gb.new-years-day",
    "New Year Holiday": "gb.new-year-holiday",
    "Saint Patrick's Day": "gb.st-patricks-day",
    "Good Friday": "gb.good-friday",
    "Easter Monday": "gb.easter-monday",
    "May Day": "gb.early-may",
    "Spring Bank Holiday": "gb.spring",
    "Battle of the Boyne": "gb.battle-of-the-boyne",
    "Summer Bank Holiday": "gb.summer",
    "Late Summer Bank Holiday": "gb.summer",
    "Saint Andrew's Day": "gb.st-andrews-day",
    "Christmas Day": "gb.christmas-day",
    "Boxing Day": "gb.boxing-day",
    "Golden Jubilee of Elizabeth II": "gb.golden-jubilee",
    "Wedding of William and Catherine": "gb.royal-wedding-2011",
    "Diamond Jubilee of Elizabeth II": "gb.diamond-jubilee",
    "Platinum Jubilee of Elizabeth II": "gb.platinum-jubilee",
    "State Funeral of Queen Elizabeth II": "gb.state-funeral-2022",
    "Coronation of Charles III": "gb.coronation-2023",
    "Scotland's participation in the FIFA World Cup final": "gb.world-cup-2026",
}

DE = {
    "Neujahr": "de.new-years-day",
    "Heilige Drei Könige": "de.epiphany",
    "Frauentag": "de.womens-day",
    "Karfreitag": "de.good-friday",
    "Ostersonntag": "de.easter-sunday",
    "Ostermontag": "de.easter-monday",
    "Erster Mai": "de.labour-day",
    "75. Jahrestag der Befreiung vom Nationalsozialismus und der Beendigung des Zweiten Weltkriegs in Europa": (
        "de.liberation-day-2020"
    ),
    "80. Jahrestag der Befreiung vom Nationalsozialismus und der Beendigung des Zweiten Weltkriegs in Europa": (
        "de.liberation-day-2025"
    ),
    "Christi Himmelfahrt": "de.ascension-day",
    "Pfingstsonntag": "de.whit-sunday",
    "Pfingstmontag": "de.whit-monday",
    "Fronleichnam": "de.corpus-christi",
    "75. Jahrestag des Aufstandes vom 17. Juni 1953": "de.uprising-1953",
    "Mariä Himmelfahrt": "de.assumption-day",
    "Weltkindertag": "de.world-childrens-day",
    "Tag der Deutschen Einheit": "de.german-unity-day",
    "Reformationstag": "de.reformation-day",
    "Allerheiligen": "de.all-saints-day",
    "Buß- und Bettag": "de.repentance-day",
    "Erster Weihnachtstag": "de.christmas-day",
    "Zweiter Weihnachtstag": "de.st-stephens-day",
}


def ua_id(name, day):
    if name == "Різдво Христове":
        return "ua.orthodox-christmas" if day.month == 1 else "ua.christmas-day"
    if name in ("День праці", "День міжнародної солідарності трудящих"):
        return "ua.labour-day-2" if day.day == 2 else "ua.labour-day"
    return {
        "Новий рік": "ua.new-years-day",
        "Міжнародний жіночий день": "ua.womens-day",
        "Великдень (Пасха)": "ua.easter",
        "Трійця": "ua.trinity",
        "День Перемоги": "ua.victory-day",
        "День перемоги над нацизмом у Другій світовій війні (День перемоги)": "ua.victory-day",
        "День Конституції України": "ua.constitution-day",
        "День незалежності України": "ua.independence-day",
        "День захисника України": "ua.defenders-day",
        "День захисників і захисниць України": "ua.defenders-day",
    }[name]


# country -> (subdivisions, years, observed suffix, name -> id, skipped name prefixes)
COUNTRIES = {
    "US": ([None], range(2000, 2051), " (observed)", lambda n, d: US[n], ()),
    "GB": ([None, "ENG", "NIR", "SCT", "WLS"], range(2000, 2051), " (observed)", lambda n, d: GB[n], ()),
    "DE": (
        [None, "BB", "BE", "BW", "BY", "HB", "HE", "HH", "MV", "NI", "NW", "RP", "SH", "SL", "SN", "ST", "TH"],
        range(2000, 2051),
        None,
        lambda n, d: DE[n],
        (),
    ),
    # From 2022 python-holidays files UA holidays under martial law as working days, so the
    # oracle covers 2000-2021. Cabinet transfers of working days are not modelled by Orrery.
    "UA": ([None], range(2000, 2022), " (вихідний)", ua_id, ("Вихідний день (перенесено з",)),
}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for cc, (subdivs, years, observed, to_id, skipped) in COUNTRIES.items():
        rows = []
        for subdiv in subdivs:
            # Default PUBLIC category: Catholic-only and school days are not included.
            for day, names in sorted(holidays.country_holidays(cc, subdiv=subdiv, years=years).items()):
                for name in names.split("; "):
                    if skipped and name.startswith(skipped):
                        continue
                    substitute = bool(observed) and name.endswith(observed)
                    base = name[: -len(observed)] if substitute else name
                    try:
                        rule_id = to_id(base, day)
                    except KeyError:
                        if substitute:
                            rule_id = ""
                        else:
                            raise
                    code = f"{cc}-{subdiv}" if subdiv else ""
                    rows.append((day.isoformat(), code, rule_id, "1" if substitute else "0"))
        with open(OUT / f"holidays-{cc.lower()}.csv", "w", newline="") as f:
            csv.writer(f, lineterminator="\n").writerows(rows)
        print(cc, len(rows))


if __name__ == "__main__":
    main()
