# Repository workflow

- After completing each agreed task, validate the changes, git commit, and git push before starting the next task, unless the user explicitly asks otherwise.
- Deliver installable APKs using the existing permanent release signing key through `scripts/build-release.sh`. Verify against `release-signing-certificate.sha256`; do not offer debug-signed or unsigned APKs for tablet updates.
