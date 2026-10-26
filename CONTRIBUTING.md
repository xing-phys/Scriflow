# Contributing to Scriflow

Scriflow currently targets one verified tablet/firmware combination. Please
read the [compatibility statement](README.md#compatibility-and-status),
[architecture](docs/architecture.md), and [build guide](docs/building.md) before
changing the handwriting or storage paths.

## Changes and verification

Keep changes focused and explain their user-visible effect. Run host tests,
Android lint, and debug/unsigned release builds using the commands in the build
guide. Add a meaningful regression test for storage, migration, geometry, or
callback-order fixes when feasible. Keep existing format fixtures and legacy
identity prefixes compatible.

Physical pen latency, visible refresh transitions, picker behavior, and sustained
writing need the supported tablet. Say which checks you ran and which still need
physical acceptance; host tests and synthetic native restoration cannot establish
these behaviors. Use disposable notebooks for device checks. Do not overwrite
personal notes or commit device captures.

## Reporting a problem

Use the [GitHub issue forms](https://github.com/xing-phys/Scriflow/issues/new/choose).

Include the app version, tablet model, exact firmware, steps to reproduce,
expected/observed behavior, and whether the issue concerns saving or only display.
For save problems, keep the original app data and a safe copy before trying
recovery. A small disposable example is preferable to sharing personal notes.

Review attachments for handwriting, notebook names, device identifiers, and
other private information. See [privacy](docs/privacy.md). Never include signing
keys, passwords, local tool directories, or recovery copies in a contribution.

Scriflow uses the [MIT License](LICENSE). Preserve its copyright and license
notice, and retain applicable [third-party notices](THIRD_PARTY_NOTICES.md).
