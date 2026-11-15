# Releasing

## One-time setup (manual, cannot be automated)

1. Create the GitHub repository and wire the remote:
   `git remote add origin git@github.com:koza4e4ok/Orrery.git && git push -u origin main`
   (If the org/repo name differs, update `POM_URL`/`POM_SCM_*` in `gradle.properties` and the
   README badge first.)
2. Verify the `me.kozakov` namespace on the [Central Portal](https://central.sonatype.com/).
3. Generate a Central Portal **user token** (Account → Generate User Token).
4. Export an ASCII-armored GPG signing key:
   `gpg --armor --export-secret-keys <KEY_ID>`
5. Add the five repository secrets used by `.github/workflows/publish.yml`:
   - `MAVEN_CENTRAL_USERNAME` / `MAVEN_CENTRAL_PASSWORD` — the user token pair
   - `SIGNING_KEY` — the armored secret key
   - `SIGNING_KEY_ID` — last 8 hex digits of the key fingerprint
   - `SIGNING_KEY_PASSWORD` — the key passphrase
6. To host the API docs: Settings → Pages → Source **GitHub Actions**, then in
   Settings → Environments → `github-pages` allow the `v*` tag pattern (by default
   the environment only accepts deployments from `main`). The `Docs` workflow
   (`.github/workflows/docs.yml`) then deploys Dokka to
   https://koza4e4ok.github.io/Orrery/ on every `v*` tag.

## Release flow

1. Set `VERSION_NAME=X.Y.Z` (drop `-SNAPSHOT`) in `gradle.properties`; update `CHANGELOG.md`
   and the install snippets in `README.md` (`implementation(...)` lines and the
   version-catalog `orrery = "X.Y.Z"`).
2. Commit: `git commit -am "release: X.Y.Z"`.
3. Tag and push: `git tag vX.Y.Z && git push origin main vX.Y.Z`.
4. The `Publish` workflow first runs the release gate: `checkKotlinAbi`,
   `tools/api-compat.py --previous-of <tag>` (the dumps against the previous `v*` tag,
   enforcing, with `tools/api-compat-allow.txt`) and the Linux test tasks of `ci.yml`.
   A final tag (no `-` suffix) also gets `--forbid-entries`: it takes no allow-list
   entries at all. A tag that fails the gate never publishes: fix it on `main` and tag a
   new version.

   > **Until task 8 generates the allow-list**, any tag pushed from `main` fails this gate,
   > a `0.9.x` patch included: `main` has 26 findings against `v0.9.2` (bridges turned
   > `synthetic` and the #129 heatmap signature changes) and `tools/api-compat-allow.txt`
   > is empty. That is intended; do not tag before task 8 lands.
   Then it builds, signs, uploads to the Central Portal, and releases automatically
   (`publishToMavenCentral(automaticRelease = true)`).
5. Bump `VERSION_NAME` to the next `X.Y.(Z+1)-SNAPSHOT`, update the snapshot version in the
   README's **Snapshots** note to match, and commit.

## ABI gate across the 1.0 release candidates

The gate (`tools/api-compat.py`, spec 2.3) compares the dumps with `API_BASELINE_REF` in
`ci.yml` and lets planned breaks through `tools/api-compat-allow.txt`. Its lifecycle is
part of the release:

1. **Freeze (task 14), before `v1.0.0-rc.1`:** set `API_COMPAT_ENFORCE: "true"` in
   `ci.yml` (exactly `true` or `false`; anything else fails the step). The PR gate is now
   required. The rc.1 tag's publish gate runs against `v0.9.2` with the generated list.
2. **First PR after `v1.0.0-rc.1` is tagged:** move `API_BASELINE_REF` to `v1.0.0-rc.1`
   and empty the allow-list. From an rc baseline on, the script fails on any entry that is
   not `rc-soak`, in report mode too. The same PR drops
   `-x :orrery-widget:publishToMavenCentral` from `publish.yml` if the widget ships.
3. **A break found during the soak:** Andrii approves it in a comment on #89. A PR makes
   the change and adds temporary entries with reason `rc-soak` and that comment's URL as the
   ledger field (`https://github.com/koza4e4ok/Orrery/issues/89#issuecomment-<id>`); the
   script checks the URL's shape, Ritsuko checks it is Andrii's at review. Tag `rc.2`: its
   publish gate runs against `rc.1` and passes on those entries. The first PR after `rc.2`
   moves the baseline to `rc.2` and empties the list again.
4. **`v1.0.0`:** the list must be empty. `publish.yml` passes `--forbid-entries` for any
   final tag, and a `1.0.0`+ baseline takes no entries either. After the release, move
   `API_BASELINE_REF` to `v1.0.0`.
5. **1.1, `orrery-widget` graduates:** drop `--require-empty orrery-widget` from `ci.yml`
   and `publish.yml`; from then on its dumps are compared like any stable module.

## Local dry-run (no credentials needed)

```bash
./gradlew publishToMavenLocal
ls ~/.m2/repository/me/kozakov/orrery/
```

Signing is skipped locally: the publish convention only calls `signAllPublications()`
when the `signingInMemoryKey` Gradle property is present.

## One version for all modules

Every `orrery-*` artifact is released from the same `VERSION_NAME`. `orrery-widget`
(experimental) uses `@InternalOrreryApi` declarations of `orrery-compose`, so it pins
that exact version: `strictly` in its Gradle module metadata and `[X.Y.Z]` for the
`me.kozakov.orrery` dependencies in its POM. Check both after a dry run:

```bash
./gradlew :orrery-widget:publishToMavenLocal
cat ~/.m2/repository/me/kozakov/orrery/orrery-widget/X.Y.Z/orrery-widget-X.Y.Z.module | grep -B2 -A6 strictly
grep -A3 '<artifactId>orrery-compose' ~/.m2/repository/me/kozakov/orrery/orrery-widget-android/X.Y.Z/*.pom
```

Gradle consumers fail fast on a mismatched `orrery-compose` version; for Maven the
`[X.Y.Z]` pin is best effort, because nearest-wins mediation lets a version the app
declares itself override it.

`@ExperimentalOrreryApi` and `@InternalOrreryApi` declarations are left out of the ABI
dumps (`abiValidation` filters in the build-logic conventions), so `orrery-widget`'s
committed dumps stay empty until it graduates; list its changes under an
"Experimental" heading in `CHANGELOG.md`.
