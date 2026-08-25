# 抖音自动续火花助手

抖音自动续火花助手是一个面向 Windows 的多账号无人值守程序。程序使用 Java 21、Playwright Java 和随包 Chromium，为每个抖音账号维护独立登录态、调度配置和发送状态，并在创作者中心聊天页中自动续火花。

> 仓库地址：[hyx823894/AutoDouyinSpark](https://github.com/hyx823894/AutoDouyinSpark)

## 功能概览

- 内置 Chromium 浏览器，普通用户不需要额外安装浏览器或 Java。
- 提供多账号工作台，支持新增、切换、重命名和可恢复归档账号。
- 每个账号独立保存配置、登录态、冷却计数、每日上限、截图和调度状态。
- 多账号启动采用错峰巡检，降低服务器瞬时浏览器启动压力。
- 首次扫码或完成登录后，后续复用专用浏览器登录态。
- 支持可独立启用的间隔模式和多时间点定时模式，两种模式可同时运行；自动触发会加入 0–5 分钟随机延迟。
- 失败会话默认一小时后独立重试一次；程序主动跳过不算失败，结果不确定时先核验原消息再决定是否重发。
- 发送使用真实鼠标轨迹、滚轮、按压停顿和逐字键盘输入；只有检测到正确会话中的新增己方消息气泡才计入成功。
- 支持服务器无界面守护；调度、页面控制和发送不依赖窗口刷新或前台焦点。
- 浏览器崩溃、页面关闭或控制连接失效后，会自动重建运行环境。
- 页面虚拟列表重绘或发送结果检测超时不会关闭正常浏览器；重复聊天标签页会自动合并为一个，非聊天标签页保持不变。
- 页面业务区域未就绪时最多等待 45 秒，超时后保留页面继续加载，不再反复刷新。
- 提供全局单实例锁与账号浏览器资料占用检测，避免多开导致 Chromium 以退出码 21 终止。
- 登录失效、验证码、风控、扫码等安全校验不会被绕过；程序会暂停发送并提示重新登录。
- 目标会话必须唯一匹配；多结果或切换结果无法确认时不会发送，避免发错好友。
- 保留原浏览器扩展源码作为兼容回退，新版主入口为桌面程序。

## 适用场景

适合在自己的 Windows 电脑上长期守护抖音创作者中心私信，用于给少量指定会话发送固定续火花提醒。请只在你有权操作的账号上使用，并遵守抖音平台规则。

## 快速使用

1. 从 GitHub Releases 下载 `douyin-auto-spark-*-windows.zip`。
2. 完整解压压缩包，必须保留 `app`、`runtime`、`browsers` 等目录。
3. 优先双击 `抖音自动续火花助手.exe` 启动程序；如果发布包内没有 `.exe`，请双击 `抖音自动续火花助手.bat`；如果没有窗口，请双击 `启动诊断.cmd` 查看中文提示。
4. 首次使用点击“打开登录/聊天页”，在内置 Chromium 中完成抖音创作者中心登录。
5. 在左侧选择账号；如需管理多个登录态，点击“新增账号”，再为当前账号打开登录页完成登录。
6. 在“发送策略”中填写当前账号的目标会话、消息、调度和安全限制并保存。
   页面底部提供固定的“保存当前账号配置”按钮，也可按 `Ctrl+S` 快速保存。
7. 服务器部署可在各账号首次登录验证成功后分别启用“服务器无界面模式”。
8. 点击“立即执行”验证当前账号，其他已启用账号会继续独立守护。
9. 程序日志、账号注册表和所有账号数据保存在 `%APPDATA%\DouyinAutoSpark`。

更多细节见 [使用说明](docs/使用说明.md)。

## 数据目录

默认数据目录为 `%APPDATA%\DouyinAutoSpark`：

| 路径 | 说明 |
| --- | --- |
| `accounts.json` | 账号注册表、当前选择和全局开机启动配置。 |
| `accounts/<账号ID>/config.json` | 当前账号独立的 UTF-8 配置。 |
| `accounts/<账号ID>/state.json` | 当前账号独立的发送次数、冷却和调度状态。 |
| `accounts/<账号ID>/browser-profile` | 当前账号独立的 Chromium 登录态。 |
| `accounts/<账号ID>/screenshots` | 当前账号独立的失败截图。 |
| `archived-accounts` | 从工作区归档的账号数据，可人工恢复。 |
| `logs` | 每日中文运行日志。 |

程序解压目录中的 `browsers` 是随程序打包的 Chromium 浏览器本体，不要删除。

## 代码结构

| 路径 | 说明 |
| --- | --- |
| `pom.xml` | Java 21 Maven 项目配置。 |
| `plugin.yml` | 插件元信息与版本号。 |
| `app/src/main/java/com/douyin/autospark` | 多账号注册中心、独立运行单元、工作台界面、调度、日志和 Playwright 自动化入口。 |
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
