# Changelog

This is the English changelog. See [CHANGELOG.md](CHANGELOG.md) for the
Chinese one; the version-numbering rationale is documented there as well.

Releases used to be tagged `v1.1-dsN`, where `dsN` named the **Droidspaces
release** the build targeted (`ds73` = Droidspaces 7.3) rather than a patch
counter. Tags are now `vMAJOR.MINOR.PATCH`: `MAJOR.MINOR` follows upstream
anland-shell's 1.1 and `PATCH` starts at 1, so this release is `v1.1.1`. The
old tags and releases are kept as-is; `v1.1-ds72` and `v1.1-ds73` are archived
here as `v1.1.1`.

## 1.1.1 - 2026-11-20

### Fixed

**The container list was empty and app launches did nothing (Droidspaces
7.2.0).** Upstream calls `droidspaces show --format`, but 7.2.0 dropped that
flag in favour of `show --json`. The old flag writes
`Unknown or incomplete option: --format` to stdout and still exits 0, so the
running set parses empty — **every container shows `stopped` and the `APPS`
entry is blocked**. The build now asks for `show --json` first, falls back to
`show --format`, and strips ANSI escapes before parsing. (Corresponds to
upstream PR #2, unmerged.)

**Container tools were not found (`/usr/bin` missing from `PATH`).**
`droidspaces run` inherits the **host (Android) PATH**, which has no
`/usr/bin`, so container tools such as `getent` are invisible. The user probe
then failed silently and every app launch reported:

```
failed to launch Kate: user Gold not found in container
```

Every snippet sent into the container now starts with:

```sh
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH
```

### CI

**A tag push builds and publishes a Release.** `v*` triggers CI and
`anland-shell.apk` is uploaded as a release asset.

### Documentation

**The README is split into English and 简体中文** (it was a single heading
before).
