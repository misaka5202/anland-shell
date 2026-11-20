# Changelog

## 版本号说明

本 fork 早先的 Release 用 `v1.1-dsN` 标记，其中 `dsN` 指的是**该版本面向的
Droidspaces 版本**（`ds73` = Droidspaces 7.3），不是补丁序号——所以序号既不
连续也不可预测。为了让版本号能像 `droidspaces-lkm` 一样一眼读出先后，
Release 改为 `vMAJOR.MINOR.PATCH`：`MAJOR.MINOR` 跟随上游 anland-shell 的
1.1，`PATCH` 按发布先后递增。

旧的 `v1.1-dsN` Release **按时间顺序对应到新的补丁号**，内容不变：

| 旧 tag | 新 tag | 日期 |
|---|---|---|
| `v1.1-ds72` | `v1.1.1` | 2026-11-16 |
| `v1.1-ds73` | `v1.1.2` | 2026-11-19 |
| （无对应旧 tag，本 fork 的 vMAJOR.MINOR.PATCH 首个发布） | `v1.1.3` | 2026-11-20 |

## v1.1.3

### 文档

**写了真正的 README**（此前只有一个标题），并**拆成英文与简体中文两份**。

### 发布方式

**Release 从 `v1.1-dsN` 改为 `vMAJOR.MINOR.PATCH`。** 版本号形如
`v1.1.3`，与 `droidspaces-lkm` 一致；Release 正文改由 `docs/CHANGELOG.en.md`
里对应版本的条目生成，不再是自动生成的 compare 链接。

## v1.1.2

### 修复

**容器内工具找不到（`/usr/bin` 不在 PATH 上）。** `droidspaces run` 继承的是
宿主（Android）的 `PATH`，里面没有 `/usr/bin`，于是 `getent` 这类容器工具
不可见。用户探测因此静默失败，每次启动应用都报：

```
failed to launch Kate: user Gold not found in container
```

现在每段发进容器的脚本都会前置：

```sh
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH
```

### CI

**打 tag 就构建并发布 Release。** `v*` 触发 CI，产物 `anland-shell.apk`
作为 Release 附件上传。

## v1.1.1

### 修复

**容器列表失效、应用启动没反应（Droidspaces 7.2.0）。** 上游调用
`droidspaces show --format`，而 7.2.0 去掉了这个参数、改用 `show --json`。
旧参数会把 `Unknown or incomplete option: --format` 写到 stdout 且仍然
`exit 0`，于是运行中集合解析为空——**每个容器都显示 `stopped`，`APPS` 入口
被挡住**。现在先取 `show --json`，失败再回退 `show --format`，并在解析前
剥离 ANSI 转义序列。（对应上游 PR #2，未合并。）
