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
| 两类调度 | 间隔模式与每日多时间点定时模式可独立或同时启用，自动触发加入 0–5 分钟随机延迟。 |
| 可靠发送 | 完整扫描虚拟会话列表，按完全匹配、包含匹配定位目标；切换结果无法确认时不发送。 |
| 结果核验 | 只有正确会话中出现发送前不存在、文本一致的己方消息气泡，才计入成功。 |
| 独立重试 | 失败或结果不确定的会话独立延迟重试；重试前先核验原消息，降低重复发送风险。 |
| 安全限制 | 支持每日上限、单会话冷却、批次休息和保守的仿人操作节奏。 |
| 无人值守 | 已完成登录后可启用服务器无界面模式；浏览器或控制连接失效时自动恢复。 |
| 本机存储 | 配置、日志和登录态默认只保存在当前 Windows 用户目录，不由程序主动上传。 |

## 下载与首次运行

1. 打开 [最新 Release](https://github.com/uxu2023259/AutoDouyinSpark/releases/latest)，下载 `douyin-auto-spark-版本-windows.zip`。
2. 完整解压压缩包。不要只复制启动文件，也不要直接在压缩包中运行。
3. 优先双击 `抖音自动续火花助手.exe`；若发布包提供便携入口，则双击 `抖音自动续火花助手.bat`。
4. 如果没有出现窗口，运行同目录下的 `启动诊断.cmd` 查看中文诊断信息。
5. 在左侧账号工作区选择账号，点击“打开登录/聊天页”。
6. 在内置 Chromium 中手动完成抖音创作者中心登录，并保持程序运行至少 15 秒以保存登录态。
7. 填写目标会话、发送内容、调度和安全限制，点击“保存当前账号配置”。
8. 先使用“立即执行”验证目标，再启用自动执行或服务器无界面模式。

完整操作说明见 [docs/使用说明.md](docs/使用说明.md)，常见故障见 [docs/常见问题.md](docs/常见问题.md)。

## 发送与调度原则

- 多个关键词命中同一会话时自动去重；同名但不同的会话分别维护冷却状态。
- 每轮只向实际命中的目标发送，配置中的主动跳过不记为失败。
- 页面列表重绘、元素暂时不可见或检测超时不会直接销毁正常浏览器。
- 登录失效或出现安全验证时暂停自动发送，等待人工处理。
- 手动执行不改变自动调度时间线；间隔模式从上一轮自动任务结束后重新计时。
- 定时与间隔任务同时到期时合并为一轮，避免短时间重复执行。

## 本地数据

运行数据默认位于 `%APPDATA%\DouyinAutoSpark`：

| 路径 | 内容 |
| --- | --- |
| `accounts.json` | 账号注册表、当前选择与全局启动设置。 |
| `accounts/<账号ID>/config.json` | 当前账号的发送和调度配置。 |
| `accounts/<账号ID>/state.json` | 成功次数、冷却、待重试和调度状态。 |
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
