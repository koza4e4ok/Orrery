# Security Policy

## Supported versions

Security fixes go to the latest release line. Once 1.0 is out, that is the latest 1.x release;
until then it is the latest 0.9.x release.

| Version | Supported |
| ------- | --------- |
| Latest release (1.x once released) | Yes |
| 0.9.x (until 1.0.0 is released) | Yes |
| Older | No |

## Reporting a vulnerability

Please report vulnerabilities **privately**. Do not open a public issue or pull request.

Use GitHub's private vulnerability reporting:
[Report a vulnerability](https://github.com/koza4e4ok/Orrery/security/advisories/new)
(Security tab → Report a vulnerability).

Include, as far as you can:

- the affected module (`orrery-core`, `orrery-lunar`, `orrery-calendars`, `orrery-holidays`,
  `orrery-compose`, `orrery-widget`) and version;
- what an attacker can do and under which conditions (for example "an application that passes
  untrusted `RRULE` text to `RecurrenceRule.parse`");
- a minimal reproduction or proof of concept.

## What to expect

- An acknowledgement within 7 days.
- An assessment and a fix plan within 30 days.
- A coordinated disclosure: we publish a GitHub security advisory and a patched release together,
  normally within 90 days of the report, and credit you unless you prefer to stay anonymous.

Orrery is maintained by one person, so these are targets rather than guarantees; if you hear
nothing within 7 days, please post a comment on the private report.

## Scope

In scope: the published artifacts (`me.kozakov.orrery:*` on Maven Central), the library
source in this repository, and the release pipeline (`.github/workflows`), for example a way
to publish or tamper with a release.

Out of scope: the `:sample` and `:demo` apps, vulnerabilities in third-party dependencies
(report those upstream; if Orrery needs to bump a version, a regular issue is fine once the
advisory is public), and denial of service that needs the application to pass unbounded input to
a documented parameter such as a window of thousands of years.

## Verifying releases

Release artifacts on Maven Central are signed with the project's GPG key and are built from `v*`
tags by the `Publish` workflow in this repository.
