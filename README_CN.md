# anland-shell

**简体中文** | [English](README.md)

anland 的配套启动器：管理 Droidspaces 容器、列出并启动容器里的 Linux 应用。
每个应用对应一个独立的 Android 窗口（`AwlWindowActivity`），入最近任务、可分屏。

上游：[SuperTurtleDev/anland-shell](https://github.com/SuperTurtleDev/anland-shell) ·
本 fork 下载：**[v1.1-ds73](https://github.com/misaka5202/anland-shell/releases/tag/v1.1-ds73)**

---

## 为什么用本 fork

上游 1.1 在 Droidspaces 7.2.0 上**容器列表失效、应用启动没反应**。`v1.1-ds73`
有两处修复：

### 1. `droidspaces show --json` 优先（上游 PR #2，未合并）

上游调用 `droidspaces show --format`：

```java
RootExec.exec(bin + " show --format", 15_000);
```

Droidspaces 7.2.0 移除了该参数，改为 `show --json`。旧参数把
`Unknown or incomplete option: --format` 写到 stdout，退出码仍是 0，于是
running 集合解析为空 —— **每个容器都显示 `stopped`，`APPS` 入口被拦**。

补丁版：先 `show --json`，失败回退 `show --format`，解析前剥离 ANSI 转义。

### 2. 每条 `droidspaces run` 片段前置容器 `PATH`

`droidspaces run` 继承的是**宿主（Android）PATH**，里面没有 `/usr/bin`，
于是容器里的 `getent` 等命令全部不可见。用户探测因此静默失败，点任何应用都报：

```
failed to launch Kate: user Gold not found in container
```

补丁版在送进容器的每段 snippet 前加：

```sh
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH
```

---

## 下载

| 版本 | 说明 |
|---|---|
| [v1.1-ds73](https://github.com/misaka5202/anland-shell/releases/tag/v1.1-ds73) | 当前；含上述两项修复 |

```sh
adb install -r anland-shell.apk
```

Release 由 tag 自动构建发布（`v*` 触发 CI）。

## 判断是否需要补丁版

```bash
su -c 'droidspaces show --json'    # 有输出
su -c 'droidspaces show --format'  # Unknown or incomplete option
# 两条同时成立 → 需要补丁版
```

## 上游合并后可换回官方包

两处修复对应上游 PR [SuperTurtleDev/anland-shell#2](https://github.com/SuperTurtleDev/anland-shell/pull/2)
（未合并）。合并后请换回官方 release。

## 构建

纯 Java，无依赖：

```sh
make        # build/anland-shell.apk（release，debug 签名）
```

需要 `JAVA_HOME`（JDK 17+）与 Android SDK。

## 相关项目

- [misaka5202/anland](https://github.com/misaka5202/anland) —— 宿主（模块 / APK / 容器会话），Release `6.2-ds6`
- [misaka5202/Droidspaces-anland6-rootfs](https://github.com/misaka5202/Droidspaces-anland6-rootfs) —— 带硬件加速的桌面 rootfs 构建器
- [misaka5202/droidspaces-lkm](https://github.com/misaka5202/droidspaces-lkm) —— 免刷内核的容器支持 LKM（含 5.x/6.x 部署文档）
