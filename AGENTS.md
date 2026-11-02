# Repository workflow

- Use `MAJOR.MINOR.PATCH` versions: increment PATCH for bug-fix releases and MINOR for new features. Use `-alpha.N`, `-beta.N`, or `-rc.N` for candidate iterations of that base version; do not use the suffix as a substitute for patch/minor bumps across separate tasks. Increase Android versionCode for every published APK and never rewrite published release tags.
- After completing each agreed task, validate the changes, git commit, and git push before starting the next task, unless the user explicitly asks otherwise.
- Deliver installable APKs using the existing permanent release signing key through `scripts/build-release.sh`. Verify against `release-signing-certificate.sha256`; do not offer debug-signed or unsigned APKs for tablet updates.
- Publish implemented features awaiting the owner's tablet testing as alpha prereleases, with the signed APK and checksum available on GitHub before asking the owner to test.
