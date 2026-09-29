# TG X 链接 → Chuckle

> **声明：本项目（代码、逆向分析、构建脚本、文档）全部由 AI 完成**，需求方只提出目标
> （“在 org.telegram.group 里点 x.com 链接要用 app.jjyy.chuckle 打开”），并在设备上完成
> LSPosed 启用与点击验证。

一个 LSPosed 模块（libxposed modern API）：在 **org.telegram.group** 里点击 `x.com` / `twitter.com`
链接时，不再交给浏览器，而是用显式的 `app.jjyy.chuckle` 组件打开。

```
Telegram 点击 https://x.com/miko_suuuu/status/2092927316703985839
        │
        ▼
org.telegram.messenger.browser.Browser.openUrl(...)   ← 被本模块 hook
        │
        ▼
Intent(ACTION_VIEW, uri).setComponent(
    app.jjyy.chuckle / app.jjyy.chuckle.platform.twitter.TwitterDispatchActivity)
```

## 原理

* Hook 点：`org.telegram.messenger.browser.Browser` 的**全部静态 `openUrl(Context, …)` 重载**
  （当前版本 12 个）。Telegram 的消息内链接点击、长按菜单、内置浏览器、系统浏览器等路径
  最终都汇聚到这里，所以在入口处拦截即可覆盖所有点击来源。
* 判定条件：scheme ∈ {http, https} 且 host ∈ {`x.com`, `*.x.com`, `twitter.com`, `*.twitter.com`}。
* 命中后直接 `startActivity` 显式组件 `app.jjyy.chuckle/.platform.twitter.TwitterDispatchActivity`
  （`exported=true`，其 intent-filter 已声明 x.com/twitter.com 的 http/https VIEW），并
  **不调用** `chain.proceed()`，即跳过 Telegram 原本的浏览器逻辑。
* 其它链接（t.me、telegra.ph、普通 http 等）原样走 `chain.proceed()`，行为不变。

模块使用 libxposed modern API：入口声明在 `META-INF/xposed/java_init.list`，作用域声明在
`META-INF/xposed/scope.list` 与 `AndroidManifest.xml` 的 `xposedscope` meta-data。

## 目录结构

```
AndroidManifest.xml                     # 模块包信息、label/description、xposedscope
res/values/strings.xml                  # 模块名与描述
src/com/xlink/tgchuckle/TgXLinkModule.java   # 主模块（hook 逻辑）
src/com/xlink/tgchuckle/InfoActivity.java    # 说明界面
resources/META-INF/xposed/java_init.list     # 入口类
resources/META-INF/xposed/module.prop        # minApiVersion / targetApiVersion / staticScope
resources/META-INF/xposed/scope.list         # org.telegram.group
tools/repack.py                         # 把 classes.dex 与 META-INF/xposed 合入 APK
build.sh                                # 无 Gradle 构建脚本
libs/                                   # 编译期依赖，不入库，build.sh 首次运行自动下载
```

## 构建（Termux / 无 Gradle）

依赖：`java`(JDK 21)、`aapt2`、`d8`、`zipalign`、`apksigner`、`python3`、`curl`。

```bash
bash build.sh
# => tg-x-link-opener.apk
```

脚本流程：`aapt2 compile/link` → `javac`（classpath 用 android API stub + libxposed api）→
`d8` → `repack.py` 打包 → `zipalign` → `apksigner`（首次运行自动生成 `debug.keystore`）。
`libs/` 下缺少的 jar 会自动从 Maven Central 下载：

* `com.google.android:android:4.1.1.4`（仅编译期用的 API stub）
* `io.github.libxposed:api:102.0.0`（.aar 里的 classes.jar）

## 安装与启用

```bash
cp tg-x-link-opener.apk /sdcard/ && su -c "mkdir -p /data/local/tmp && cp /sdcard/tg-x-link-opener.apk /data/local/tmp/ && pm install -r /data/local/tmp/tg-x-link-opener.apk"
```

LSPosed 会自动扫描到模块，并按 `scope.list` 预置作用域（本机实测 `modules_config.db` 中
`enabled=1`、scope 已含 `org.telegram.group`）。若未自动启用，请在 LSPosed 管理器中手动勾选。

启用后**强制停止 Telegram 再打开**（新进程才会注入），然后在 Telegram 中点击任意 x.com 链接。

## 验证

模块日志走 LSPosed：管理器日志页，或

```bash
su -c "grep TgXLink /data/adb/lspd/log/modules_*.log | tail"
```

预期输出：

```
module loaded, process=org.telegram.group, api=102, framework=LSPosed 2.2.0
hooked openUrl(12 args)      # 共 12 个重载
install done, hooks=12
redirect https://x.com/xxx/status/123 -> app.jjyy.chuckle
```

## 已知限制

* 仅作用于 `org.telegram.group`（scope 可自行扩大，但 hook 点依赖 Telegram 的类结构）。
* 只处理 `x.com` / `twitter.com` 的 http(s) 链接，其它链接不受影响。
* 依赖 Telegram 保留 `Browser.openUrl` 这一入口；若未来版本改动调用路径，
  需要补充 hook 点（例如直接 `Context.startActivity` 的 VIEW intent）。

## 许可证

[MIT](LICENSE)
