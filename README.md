# anland-shell

**English** | [简体中文](README_CN.md)

The companion launcher for anland: it manages Droidspaces containers and lists
and starts the Linux apps inside them. Every app gets its own Android window
(`AwlWindowActivity`), with its own recents entry and split-screen support.

Upstream: [SuperTurtleDev/anland-shell](https://github.com/SuperTurtleDev/anland-shell) ·
Fork downloads: **[v1.1.3](https://github.com/misaka5202/anland-shell/releases/tag/v1.1.3)**

---

## Why this fork

Upstream 1.1 breaks on Droidspaces 7.2.0: the container list comes up empty and
tapping an app does nothing. `v1.1.3` carries both fixes.

### 1. Ask for `droidspaces show --json` first (upstream PR #2, unmerged)

Upstream calls `droidspaces show --format`:

```java
RootExec.exec(bin + " show --format", 15_000);
```

Droidspaces 7.2.0 dropped that flag in favour of `show --json`. The old flag
writes `Unknown or incomplete option: --format` to stdout and still exits 0, so
the running set parses empty — **every container shows `stopped` and the `APPS`
entry is blocked**.

The patched build asks for `show --json` first, falls back to `show --format`,
and strips ANSI escapes before parsing.

### 2. Prepend the container `PATH` to every `droidspaces run` snippet

`droidspaces run` inherits the **host (Android) PATH**, which has no
`/usr/bin`, so container tools such as `getent` are invisible. The user probe
then fails silently and every app launch reports:

```
failed to launch Kate: user Gold not found in container
```

The patched build prepends this to every snippet it sends into the container:

```sh
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH
```

---

## Download

| Version | Notes |
|---|---|
| [v1.1.3](https://github.com/misaka5202/anland-shell/releases/tag/v1.1.3) | current; both fixes below |

```sh
adb install -r anland-shell.apk
```

Releases are built and published automatically on tag push (`v*` triggers CI).

Full release history: [docs/CHANGELOG.md](docs/CHANGELOG.md).

## How to tell whether you need the patched build

```bash
su -c 'droidspaces show --json'    # produces output
su -c 'droidspaces show --format'  # Unknown or incomplete option
# both true -> you need the patched build
```

## Switch back to the official build once upstream merges

Both fixes correspond to upstream PR
[SuperTurtleDev/anland-shell#2](https://github.com/SuperTurtleDev/anland-shell/pull/2)
(unmerged). When it lands, use the official release again.

## Build

Pure Java, no dependencies:

```sh
make        # build/anland-shell.apk (release, debug-signed)
```

Needs `JAVA_HOME` (JDK 17+) and the Android SDK.

## Related projects

- [misaka5202/anland](https://github.com/misaka5202/anland) — the host (module / APK / container session), release `v6.2.8`
- [misaka5202/Droidspaces-anland6-rootfs](https://github.com/misaka5202/Droidspaces-anland6-rootfs) — hardware-accelerated desktop rootfs builder
- [misaka5202/droidspaces-lkm](https://github.com/misaka5202/droidspaces-lkm) — container-support LKM without a kernel flash (includes the 5.x/6.x deployment docs)
