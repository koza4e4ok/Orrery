# API stability

From 1.0.0, Orrery follows [semantic versioning](https://semver.org/) for its stable public
API: a minor or patch release never removes or changes a stable declaration in a way that
breaks your source code or a binary compiled against an earlier 1.x release. Breaking
changes are possible only in a major release (2.0), and only after a deprecation cycle.

Coming from 0.9.x? 1.0.0 itself is a one-time break; see the
[0.9 to 1.0 migration guide](migration/0.9-to-1.0.md).

## What is covered

The promise covers the **stable** public declarations of Orrery's own modules:
`orrery-core`, `orrery-compose`, `orrery-lunar`, `orrery-calendars`, `orrery-holidays` and
`orrery-widget` (which has no stable declarations in 1.0.x, see below).

A declaration is stable unless it carries one of the two opt-in markers from
`orrery-core` (package `me.kozakov.orrery`):

| Marker | Opt-in level | Meaning |
| --- | --- | --- |
| none | - | **Stable.** Source and binary compatible within 1.x. |
| `@ExperimentalOrreryApi` | warning | **Experimental.** Public and documented, but it can change or disappear in any release, including a patch release, without a deprecation cycle. Every declaration of `orrery-widget` is experimental in 1.0.x. Changes are listed under "Experimental" in the [CHANGELOG](../CHANGELOG.md). |
| `@InternalOrreryApi` | error | **Internal.** Public only so that one `orrery-*` module can use it from another. Not part of the API: no promise, not in the API reference, and unsupported outside this repository. It only works when every `orrery-*` artifact has the same version. |

Opting in (`@OptIn(ExperimentalOrreryApi::class)` or the `-opt-in` compiler flag) is how you
acknowledge that the experimental API may change under you.

**Experimental API graduates** by removing the marker after an API review. For
`orrery-widget` the plan is 1.1, once it has been used by the sample and at least one real
app for a release. From then on it is stable like the rest and covered by the gate.

## The rules for the 1.0 API

These rules apply to every stable declaration, new or old:

1. **Growth-prone types are plain classes.** Configuration, state and result types are
   regular classes with constructor defaults and a hand-written `copy`, so they can gain a
   property in a minor release. A public `data class` is only used for small, closed value
   types that can never grow (for example `ByDay` or `LunarDate`). Do not rely on
   destructuring a type unless it is a `data class`.
2. **New parameters are appended with defaults.** When a function or constructor gains a
   parameter, the new parameter comes with a default value and the old signature stays as a
   hidden overload, so existing source and binaries keep working. 1.0.0 starts without any
   such overloads.
3. **Enums and sealed types may gain members.** Public enums and sealed types that may grow
   (for example `Frequency`, `SelectionMode`, `RangeEndpointStyle`, `TodayIndicator`) say so
   in their KDoc. A new entry or subtype is not a breaking change: do not rely on an
   exhaustive `when` over them across versions; add an `else` branch.
4. **Visibility.** Everything that is not meant for callers is `internal`. A stable
   signature never mentions an internal, experimental or internal-API type, and opt-in
   markers stay out of stable signatures.
5. **Composables.** `modifier` is the first optional parameter; state and content slots are
   documented parameters; no default argument reads mutable global state.
6. **Deprecation.** A stable declaration is first deprecated with level `WARNING` and a
   `ReplaceWith` for at least one minor release, then becomes `HIDDEN` (still there for
   binaries, gone for source), and is removed only in a major release.
7. **Experimental and internal API** follow the markers above, not these rules.

## How the ABI gate works

Every pull request is checked against the last release, so a stable declaration cannot be
removed or changed by accident:

- Each module commits a text dump of its public ABI (`api/` folders: JVM dumps and klib
  dumps for iOS and Wasm). `checkKotlinAbi` makes sure the dump matches the code.
- `tools/api-compat.py` then compares the dump with the dump of the **baseline**, a release
  tag (`API_BASELINE_REF` in `.github/workflows/ci.yml`). Every line of the baseline must
  still be in the new dump. Additions are fine and are listed in the CI summary; a missing
  line (a removed class or member, a changed signature) or a lost platform target fails the
  check.
- The same check runs before every publish to Maven Central, so a release that breaks the
  previous one cannot be published.

**The allow-list.** The only way past the gate is an entry in `tools/api-compat-allow.txt`,
and each entry must name a row of the 1.0 audit ledger, [`docs/api-audit-1.0.md`](api-audit-1.0.md),
and an allowed reason. Entries are generated from the ledger by the script, not written by
hand, and are reviewed. The list exists for the deliberate 1.0 breaks while the baseline is
`v0.9.2`:

| Baseline | Allow-list |
| --- | --- |
| `v0.9.2` (until `1.0.0-rc.1` is tagged) | the 1.0 breaks from the ledger |
| `v1.0.0-rc.1` (from the first change after the RC tag) | empty; a break found during the soak needs the maintainer's approval, temporary `rc-soak` entries and a new RC, after which the baseline moves to that RC and the list is emptied again |
| `v1.0.0` and later | always empty; a final release takes no entries at all |

**What the gate does not see:**

- `@ExperimentalOrreryApi` and `@InternalOrreryApi` declarations are filtered out of the
  dumps, so the gate does not cover them. This is intended. CI also checks that the
  `orrery-widget` dump stays empty, so no unmarked declaration slips in there.
- Compose compiler artefacts that a text dump does not capture (default-argument methods,
  group keys) and behaviour. Behaviour is protected by unit tests and screenshot tests
  instead.

## Platform and toolchain

The promise covers Orrery's own API only. Orrery 1.0 is built and tested with:

| Kotlin | Compose Multiplatform | Compose Multiplatform material3 | Compose BOM (Android) | kotlinx-datetime | Android `compileSdk` / `minSdk` |
| --- | --- | --- | --- | --- | --- |
| 2.4.21 | 1.12.2 | 1.9.0 | 2026.09.00 | 0.8.0 | 37 / 23 |

These "tested with" versions are updated with each release and listed in the
[README](../README.md#stability-and-compatibility). Orrery does not promise a range of
Compose or Kotlin versions (there is no CI matrix), and the Compose compiler's internals and
Compose's own API changes are outside Orrery's promise.
