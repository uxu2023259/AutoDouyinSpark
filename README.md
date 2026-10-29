# 抖音自动续火花助手

面向 Windows 的抖音创作者中心多账号自动续火花桌面程序。每个账号拥有独立登录态、配置、调度器和发送记录，程序通过 Playwright 与内置 Chromium 完成低频、可核验的页面操作。

[![自动发布](https://github.com/uxu2023259/AutoDouyinSpark/actions/workflows/auto-release.yml/badge.svg)](https://github.com/uxu2023259/AutoDouyinSpark/actions/workflows/auto-release.yml)
[![最新版本](https://img.shields.io/github/v/release/uxu2023259/AutoDouyinSpark?label=最新版本)](https://github.com/uxu2023259/AutoDouyinSpark/releases/latest)
[![运行环境](https://img.shields.io/badge/运行环境-Windows%2010%2F11-0078D4)](https://github.com/uxu2023259/AutoDouyinSpark/releases/latest)

> 本程序只适用于你拥有或获授权操作的账号。它不会绕过登录、扫码、验证码、风控或平台安全校验，也不应被用于骚扰、刷屏或未经同意的营销。

## 核心能力

| 能力 | 当前实现 |
| --- | --- |
| 多账号隔离 | 支持新增、切换、重命名和归档账号；配置、状态、截图与浏览器资料按账号隔离。 |
| 每日计划 | 每个账号每天按一个本机 `HH:mm` 时间处理一次名单；晚启动时继续处理当天未处理的用户。 |
| 精确选人 | 只接受完整用户名匹配；相似名称不匹配，多个同名会话会标记歧义且不发送。 |
| 结果核验 | 只有正确会话中出现发送前不存在、文本一致的己方消息气泡，才计入成功。 |
| 逐人进度 | 每个用户独立持久化本日结果；失败、未找到、重名、未确认和暂停都会显示，不会误报全员完成。 |
| 防重复 | 每个用户本日最多尝试一次；失败或结果不确定时不自动重发，避免重复消息。 |
| 低资源自动化 | 自动发送默认使用无界面 Chromium；每日任务结束后关闭浏览器并保留登录资料。 |
| 本机存储 | 配置、日志和登录态默认只保存在当前 Windows 用户目录，不由程序主动上传。 |

## 下载与首次运行

1. 打开 [最新 Release](https://github.com/uxu2023259/AutoDouyinSpark/releases/latest)，下载 `douyin-auto-spark-版本-windows.zip`。
2. 完整解压压缩包。不要只复制启动文件，也不要直接在压缩包中运行。
3. 优先双击 `抖音自动续火花助手.exe`；若发布包提供便携入口，则双击 `抖音自动续火花助手.bat`。
4. 如果没有出现窗口，运行同目录下的 `启动诊断.cmd` 查看中文诊断信息。
5. 在左侧账号工作区选择账号，点击“打开登录/聊天页”。
6. 在内置 Chromium 中手动完成抖音创作者中心登录，并保持程序运行至少 15 秒以保存登录态。
7. 每行填写一个完整用户名，设置每日发送时间和消息内容，再点击“保存当前账号配置”。
8. 启用每日自动发送；到达设定时间后程序会逐个处理名单，并在日志中记录每人的结果。

自动发送默认使用无界面浏览器，并在当天名单处理完成或暂停后关闭 Chromium；手动打开的登录窗口会保持可见，直到用户关闭。

完整操作说明见 [docs/使用说明.md](docs/使用说明.md)，常见故障见 [docs/常见问题.md](docs/常见问题.md)。

## 发送与调度原则

- 名单每行对应一个指定用户，只向完整名称匹配且唯一的会话发送。
- 找不到或同名会话不明确时不发送，并把具体原因记录到日志。
- 每个用户的结果单独保存；一位用户失败不会让其他用户被跳过。
- 每天每位用户最多尝试一次；失败或结果不确定不会在当天自动重试。
- 页面列表重绘、元素暂时不可见或检测超时不会直接销毁正常浏览器。
- 自动发送完成或暂停后释放 Chromium；用户主动打开的登录窗口保持可用。
- 登录失效或出现安全验证时暂停自动发送，等待人工处理。

## 本地数据

运行数据默认位于 `%APPDATA%\DouyinAutoSpark`：

| 路径 | 内容 |
| --- | --- |
| `accounts.json` | 账号注册表、当前选择与全局启动设置。 |
| `accounts/<账号ID>/config.json` | 当前账号的每日时间、指定用户和消息内容。 |
| `accounts/<账号ID>/state.json` | 每位用户本日的处理结果与账号暂停状态。 |
| `accounts/<账号ID>/browser-profile` | Chromium 登录态，属于敏感数据。 |
| `accounts/<账号ID>/screenshots` | 失败截图，可能包含私信页面信息。 |
| `archived-accounts` | 已归档账号的完整数据。 |
| `logs` | 按日期写入的中文运行日志。 |

不要上传或分享 `browser-profile`、真实日志、失败截图、二维码、Cookie、Token 或包含真实私信的页面快照。详细边界见 [docs/安全与合规.md](docs/安全与合规.md)。

## 仓库结构

```text
app/src/main/java/com/douyin/autospark/  Java 桌面程序与自动化核心
app/src/main/resources/                 页面自动化脚本
app/src/test/java/                      单元与安全回归测试
app/src/test/resources/                 脱敏离线测试资源
docs/                                   用户、排障、安全和开发文档
.github/                                问题模板与自动发布工作流
build.ps1                               统一测试、升版和 Windows 打包入口
build/version.json                      当前构建基准版本
pom.xml                                 Maven 与 Java 21 配置
plugin.yml                              项目入口与版本元数据
```

仓库只保留当前 Java 桌面程序。历史浏览器扩展实现已经停止维护，不再参与构建或发布。

## 开发与构建

维护环境：Windows 10/11 x64、JDK 21、Maven、PowerShell 7。首次构建需要网络下载 Maven 依赖和 Playwright Chromium。

```powershell
mvn -DskipTests=false test
pwsh -ExecutionPolicy Bypass -File .\build.ps1
```

`build.ps1` 会执行测试、递增补丁版本、同步 `pom.xml`、`plugin.yml` 与 `build/version.json`，清空 `PLUGIN`，然后只生成本次 Windows 压缩包。普通用户无需单独安装 Java 或浏览器。

更多维护细节见 [docs/开发与发布.md](docs/开发与发布.md) 和 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 自动发布

`main` 分支的程序或构建配置发生变化后，[自动发布工作流](https://github.com/uxu2023259/AutoDouyinSpark/actions/workflows/auto-release.yml) 会：

1. 在 Windows Runner 上执行测试与完整打包。
2. 自动递增并提交版本号。
3. 创建对应的 `v版本号` 标签。
4. 创建 GitHub Release 并上传 Windows 压缩包。

GitHub 托管环境无法生成 `jpackage` 镜像时，构建会自动生成包含完整 Java 运行时的便携目录，发布包使用 `.bat` 启动入口；本地支持的 JDK 环境会优先生成 `.exe`。

## 相关文档

- [使用说明](docs/使用说明.md)
- [常见问题](docs/常见问题.md)
- [安全与合规](docs/安全与合规.md)
- [开发与发布](docs/开发与发布.md)
- [更新日志](CHANGELOG.md)
- [安全问题反馈](SECURITY.md)

## 许可证

仓库当前未声明开源许可证。未获得作者明确授权前，请勿复制、分发或商用本项目代码与构建产物。
