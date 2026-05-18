# 抖音自动续火花助手

抖音自动续火花助手是一个面向 Windows 的无人值守桌面程序。程序使用 Java 21、Playwright Java 和随包 Chromium，在已登录的抖音创作者中心聊天页中，按配置自动巡检指定会话并发送续火花消息。

> 仓库地址：[hyx823894/AutoDouyinSpark](https://github.com/hyx823894/AutoDouyinSpark)

## 功能概览

- 内置 Chromium 浏览器，普通用户不需要额外安装浏览器或 Java。
- 首次扫码或完成登录后，后续复用专用浏览器登录态。
- 支持巡检间隔、固定触发时间、目标会话关键词、发送内容、每日上限、冷却时间和失败重试次数。
- 发送失败时会自动重试、刷新页面，并保存中文日志和失败截图。
- 登录失效、验证码、风控、扫码等安全校验不会被绕过；程序会暂停发送并提示重新登录。
- 未检测到可交互 Windows 桌面会话时，只记录等待状态，不启动可见浏览器。
- 保留原浏览器扩展源码作为兼容回退，新版主入口为桌面程序。

## 适用场景

适合在自己的 Windows 电脑上长期守护抖音创作者中心私信，用于给少量指定会话发送固定续火花提醒。请只在你有权操作的账号上使用，并遵守抖音平台规则。

## 快速使用

1. 从 GitHub Releases 下载 `douyin-auto-spark-*-windows.zip`。
2. 完整解压压缩包，必须保留 `app`、`runtime`、`browsers` 等目录。
3. 优先双击 `抖音自动续火花助手.exe` 启动程序；如果发布包内没有 `.exe`，请双击 `抖音自动续火花助手.bat`；如果没有窗口，请双击 `启动诊断.cmd` 查看中文提示。
4. 首次使用点击“打开登录/聊天页”，在内置 Chromium 中完成抖音创作者中心登录。
5. 在程序窗口填写目标会话关键词、发送内容、巡检间隔、固定触发时间、每日上限、冷却时间和重试次数。
6. 点击“保存配置”，再点击“立即执行一次”验证流程。
7. 程序日志、配置、状态和失败截图保存在 `%APPDATA%\DouyinAutoSpark`。

更多细节见 [使用说明](docs/使用说明.md)。

## 数据目录

默认数据目录为 `%APPDATA%\DouyinAutoSpark`：

| 路径 | 说明 |
| --- | --- |
| `config.json` | UTF-8 配置文件。 |
| `state.json` | 每日发送次数、冷却状态、固定时间触发记录。 |
| `browser-profile` | Playwright 专用 Chromium 登录态目录。 |
| `logs` | 每日中文运行日志。 |
| `screenshots` | 失败截图。 |

程序解压目录中的 `browsers` 是随程序打包的 Chromium 浏览器本体，不要删除。

## 代码结构

| 路径 | 说明 |
| --- | --- |
| `pom.xml` | Java 21 Maven 项目配置。 |
| `plugin.yml` | 插件元信息与版本号。 |
| `app/src/main/java/com/douyin/autospark` | Windows 桌面程序、调度器、配置、日志和 Playwright 自动化入口。 |
| `app/src/main/resources/content-automation.js` | 在抖音页面内执行的会话定位、切换、写入和发送脚本。 |
| `app/src/test/java/com/douyin/autospark` | 配置、冷却、版本同步和离线页面选择器测试。 |
| `src` | 保留的浏览器扩展源码，用作兼容回退。 |
| `build.ps1` | 统一版本、测试、打包、jpackage 和产物输出脚本。 |
| `docs` | 使用、开发、发布、安全和常见问题文档。 |

## 开发与构建

开发环境建议使用 Windows 10/11、JDK 21、Maven 和 PowerShell 7。

```powershell
mvn -DskipTests=false test
pwsh -ExecutionPolicy Bypass -File .\build.ps1
```

构建脚本会自动递增版本号，并同步更新 `pom.xml`、`plugin.yml`、`src/manifest.json` 和 `build/version.json`。构建前会清空 `PLUGIN` 目录，构建完成后只放入本次生成的 Windows 程序压缩包。

完整说明见 [开发与发布](docs/开发与发布.md)。

## GitHub 自动发布

仓库包含 `.github/workflows/auto-release.yml`，支持在 `main` 分支源码变更后自动完成以下流程：

1. 在 Windows Runner 上设置 JDK 21。
2. 执行 `build.ps1`，自动递增版本并构建完整 Windows 压缩包。
3. 提交版本号变更到 `main`。
4. 创建 `v版本号` 标签。
5. 创建 GitHub Release 并上传 `douyin-auto-spark-*-windows.zip`。

如需手动发布，也可以在 GitHub Actions 页面运行“自动构建并发布版本”工作流。仓库需要允许 `GITHUB_TOKEN` 具备写入权限，路径为：仓库 Settings → Actions → General → Workflow permissions → Read and write permissions。

## GitHub 发布建议

- 不要把 `target`、`build`、展开后的 `PLUGIN` 目录、浏览器登录态、日志或截图提交到仓库。
- 发布给普通用户的压缩包会由 GitHub Actions 上传到 GitHub Releases，不建议直接提交到源码仓库。
- 上传前请执行测试，并确认离线页面快照不包含真实账号、头像、昵称、消息内容或 Cookie。
- 本仓库已准备 `.gitignore`、`.gitattributes`、贡献说明、问题模板、安全说明和自动发布工作流。

## 安全与合规

- 程序不会绕过登录、验证码、风控或平台安全校验。
- 程序只复用当前 Windows 用户本机的专用浏览器登录态。
- 不要公开上传 `%APPDATA%\DouyinAutoSpark`、浏览器资料目录、日志截图或包含真实私信的页面快照。
- 使用前请确认自动发送内容、频率和目标会话符合平台规则和对方预期。

详见 [安全与合规](docs/安全与合规.md)。

## 许可证

当前仓库暂未声明开源许可证。未获得作者明确授权前，请不要复制、分发或商用本项目代码与构建产物。
