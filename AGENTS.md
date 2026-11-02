# Repository workflow

- Use `MAJOR.MINOR.PATCH` versions. Choose the base version for the release scope: PATCH for a separate bug-fix release, MINOR for a new-feature release. Fixes within an ongoing alpha/beta/rc cycle may increment only the prerelease number (for example, `0.36.1-alpha.1` → `0.36.1-alpha.2`); a new task does not itself require a PATCH bump. Increase Android versionCode for every published APK and never rewrite published release tags.
- After completing each agreed task, validate the changes, git commit, and git push before starting the next task, unless the user explicitly asks otherwise.
- Deliver installable APKs using the existing permanent release signing key through `scripts/build-release.sh`. Verify against `release-signing-certificate.sha256`; do not offer debug-signed or unsigned APKs for tablet updates.
- Publish implemented features awaiting the owner's tablet testing as alpha prereleases, with the signed APK and checksum available on GitHub before asking the owner to test.
