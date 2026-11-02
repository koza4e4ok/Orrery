# Contributing to Orrery

Thanks for helping. Bug reports, questions and pull requests are all welcome.

- **Bugs and feature requests:** open an [issue](https://github.com/koza4e4ok/Orrery/issues).
  For a bug, include the Orrery version, the platform (Android, iOS, Desktop or Web)
  and a minimal snippet.
- **First contribution?** Issues labelled
  [`good first issue`](https://github.com/koza4e4ok/Orrery/labels/good%20first%20issue)
  are a good place to start.

## Pull requests

- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/)
  with the issue number, as in the history: `feat(compose): … (#84)`.
- Add a line under `## [Unreleased]` in [CHANGELOG.md](CHANGELOG.md) for anything
  users will notice.
- If you change public API, run `./gradlew updateKotlinAbi` and commit the updated
  dumps; CI's `checkKotlinAbi` fails otherwise.
- If you change how something looks, refresh the screenshot goldens with
  `./gradlew :orrery-compose:recordRoborazziDebug`.
- CI must pass. The commands below run the same checks locally.

## Development

```bash
# Full verification (what CI runs):
./gradlew ktlintCheck detekt checkKotlinAbi \
  :orrery-core:allTests :orrery-lunar:allTests :orrery-calendars:allTests :orrery-holidays:allTests \
  :orrery-compose:testDebugUnitTest :orrery-compose:verifyRoborazziDebug \
  :orrery-compose:desktopTest :orrery-compose:wasmJsTest \
  :sample:assembleDebug
./gradlew :orrery-compose:iosSimulatorArm64Test    # iOS tests (macOS only)

./gradlew :orrery-compose:recordRoborazziDebug  # refresh screenshot goldens
./gradlew updateKotlinAbi                          # refresh ABI dumps after API changes
```

`orrery-compose` is a Compose Multiplatform module targeting Android, Desktop
(`jvm("desktop")`), iOS (`iosArm64`, `iosSimulatorArm64`; Compose Multiplatform
has no Intel simulator build, so no `iosX64`) and Web (`wasmJs`). Locale-sensitive dates come from the platform: java.time on Android
and Desktop, `NSDateFormatter` on iOS, `Intl.DateTimeFormat` on Web. Built-in
strings are Android string resources on Android and bundled tables with the
same six languages elsewhere. Tests shared by Desktop, iOS and Web live in
`src/nonAndroidTest`; iOS klibs compile on Linux, but iOS tests need macOS.

On Android it uses the Android-KMP library plugin:
Android tests live in `src/androidHostTest`, and `testDebugUnitTest` /
`verifyRoborazziDebug` / `recordRoborazziDebug` are aliases for
`testAndroidHostTest` / `verifyRoborazziAndroidHostTest` /
`recordRoborazziAndroidHostTest`. To filter tests, call the real task, e.g.
`./gradlew :orrery-compose:testAndroidHostTest --tests "*DateInputFieldTest"`.

API docs: `./gradlew :dokkaGeneratePublicationHtml` writes the combined Dokka
site for `orrery-core`, `orrery-lunar`, `orrery-calendars`, `orrery-holidays` and `orrery-compose` to
`build/dokka/html`. The `Docs` workflow builds it on every `v*` tag (or by hand)
and deploys it to the [hosted API reference](https://koza4e4ok.github.io/Orrery/).

Releases are documented in [docs/RELEASING.md](docs/RELEASING.md).
