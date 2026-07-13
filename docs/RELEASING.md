# Releasing

## One-time setup (manual, cannot be automated)

1. Create the GitHub repository and wire the remote:
   `git remote add origin git@github.com:koza4e4ok/Orrery.git && git push -u origin master`
   (If the org/repo name differs, update `POM_URL`/`POM_SCM_*` in `gradle.properties` and the
   README badge first.)
2. Verify the `dev.koza4e4ok` namespace on the [Central Portal](https://central.sonatype.com/).
3. Generate a Central Portal **user token** (Account → Generate User Token).
4. Export an ASCII-armored GPG signing key:
   `gpg --armor --export-secret-keys <KEY_ID>`
5. Add the five repository secrets used by `.github/workflows/publish.yml`:
   - `MAVEN_CENTRAL_USERNAME` / `MAVEN_CENTRAL_PASSWORD` — the user token pair
   - `SIGNING_KEY` — the armored secret key
   - `SIGNING_KEY_ID` — last 8 hex digits of the key fingerprint
   - `SIGNING_KEY_PASSWORD` — the key passphrase

## Release flow

1. Set `VERSION_NAME=X.Y.Z` (drop `-SNAPSHOT`) in `gradle.properties`; update `CHANGELOG.md`.
2. Commit: `git commit -am "release: X.Y.Z"`.
3. Tag and push: `git tag vX.Y.Z && git push origin master vX.Y.Z`.
4. The `Publish` workflow builds, signs, uploads to the Central Portal, and releases
   automatically (`publishToMavenCentral(automaticRelease = true)`).
5. Bump `VERSION_NAME` to the next `X.Y.(Z+1)-SNAPSHOT` and commit.

## Local dry-run (no credentials needed)

```bash
./gradlew publishToMavenLocal
ls ~/.m2/repository/dev/koza4e4ok/orrery/
```

Signing is skipped locally: the publish convention only calls `signAllPublications()`
when the `signingInMemoryKey` Gradle property is present.
