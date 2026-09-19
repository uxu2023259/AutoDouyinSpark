$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [Console]::OutputEncoding

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$buildDir = Join-Path $root "build"
$pluginDir = Join-Path $root "PLUGIN"
$versionFile = Join-Path $buildDir "version.json"
$pluginYaml = Join-Path $root "plugin.yml"
$pomPath = Join-Path $root "pom.xml"
$bundledBrowsersDir = Join-Path $buildDir "bundled-browsers"

function Get-NextVersion {
  $versionData = Get-Content -LiteralPath $versionFile -Raw -Encoding UTF8 | ConvertFrom-Json
  $parts = $versionData.version.Split(".")
  if ($parts.Count -ne 3) {
    throw "版本号格式必须为 x.y.z"
  }
  $patch = [int]$parts[2] + 1
  return "$($parts[0]).$($parts[1]).$patch"
}

function Set-JsonVersion {
  param(
    [string]$Path,
    [string]$Version
  )

  $json = Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json
  $json.version = $Version
  $content = $json | ConvertTo-Json -Depth 10
  [System.IO.File]::WriteAllText($Path, $content + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))
}

function Set-PluginYamlVersion {
  param(
    [string]$Path,
    [string]$Version
  )

  $content = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
  $updated = [System.Text.RegularExpressions.Regex]::Replace($content, "(?m)^version:\s*.+$", "version: $Version")
  [System.IO.File]::WriteAllText($Path, $updated, [System.Text.UTF8Encoding]::new($false))
}

function Set-PomVersion {
  param(
    [string]$Path,
    [string]$Version
  )

  $content = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
  $pattern = "(?s)(<artifactId>douyin-auto-spark</artifactId>\s*<version>)([^<]+)(</version>)"
  $updated = [System.Text.RegularExpressions.Regex]::Replace($content, $pattern, "`${1}$Version`${3}", 1)
  $escapedVersion = [System.Text.RegularExpressions.Regex]::Escape($Version)
  if ($updated -eq $content -and $content -notmatch ("<version>" + $escapedVersion + "</version>")) {
    throw "未能更新 pom.xml 版本号"
  }
  [System.IO.File]::WriteAllText($Path, $updated, [System.Text.UTF8Encoding]::new($false))
}

function Assert-VersionSync {
  param([string]$Version)

  $pomText = Get-Content -LiteralPath $pomPath -Raw -Encoding UTF8
  $pluginText = Get-Content -LiteralPath $pluginYaml -Raw -Encoding UTF8
  $versionData = Get-Content -LiteralPath $versionFile -Raw -Encoding UTF8 | ConvertFrom-Json
  $escapedVersion = [System.Text.RegularExpressions.Regex]::Escape($Version)
  if ($pomText -notmatch ("<version>" + $escapedVersion + "</version>")) {
    throw "pom.xml 版本未同步为 $Version"
  }
  if ($pluginText -notmatch ("(?m)^version:\s*" + $escapedVersion + "\s*$")) {
    throw "plugin.yml 版本未同步为 $Version"
  }
  if ($versionData.version -ne $Version) {
    throw "build/version.json 版本未同步为 $Version"
  }
}

function Invoke-NativeProcess {
  param(
    [Parameter(Mandatory = $true)]
    [string]$FilePath,
    [Parameter(Mandatory = $true)]
    [string[]]$Arguments
  )

  $processInfo = [System.Diagnostics.ProcessStartInfo]::new()
  $processInfo.FileName = $FilePath
  $processInfo.UseShellExecute = $false
  $processInfo.RedirectStandardOutput = $true
  $processInfo.RedirectStandardError = $true
  $processInfo.StandardOutputEncoding = [System.Text.UTF8Encoding]::new($false)
  $processInfo.StandardErrorEncoding = [System.Text.UTF8Encoding]::new($false)
  $processInfo.WorkingDirectory = $root

  foreach ($key in @(
      "JPACKAGE_ARGS",
      "JPACKAGE_OPTIONS",
      "JAVA_TOOL_OPTIONS",
      "JDK_JAVA_OPTIONS",
      "_JAVA_OPTIONS",
      "JAVA_OPTIONS"
    )) {
    [void]$processInfo.Environment.Remove($key)
  }

  foreach ($argument in $Arguments) {
    [void]$processInfo.ArgumentList.Add($argument)
  }

  $process = [System.Diagnostics.Process]::new()
  $process.StartInfo = $processInfo
  [void]$process.Start()
  $standardOutputTask = $process.StandardOutput.ReadToEndAsync()
  $standardErrorTask = $process.StandardError.ReadToEndAsync()
  $process.WaitForExit()
  $standardOutput = $standardOutputTask.GetAwaiter().GetResult()
  $standardError = $standardErrorTask.GetAwaiter().GetResult()

  if (-not [string]::IsNullOrWhiteSpace($standardOutput)) {
    Write-Host $standardOutput.TrimEnd()
  }
  if (-not [string]::IsNullOrWhiteSpace($standardError)) {
    Write-Host $standardError.TrimEnd()
  }

  return $process.ExitCode
}

function Copy-DirectoryContents {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Source,
    [Parameter(Mandatory = $true)]
    [string]$Destination,
    [Parameter(Mandatory = $true)]
    [string]$Description
  )

  if (-not (Test-Path -LiteralPath $Source -PathType Container)) {
    throw "$Description 不存在：$Source"
  }

  $entries = @(Get-ChildItem -LiteralPath $Source -Force)
  if ($entries.Count -eq 0) {
    throw "$Description 为空：$Source"
  }

  New-Item -ItemType Directory -Path $Destination -Force | Out-Null
  foreach ($entry in $entries) {
    Copy-Item -LiteralPath $entry.FullName -Destination $Destination -Recurse -Force
  }
}

function New-PortableAppImage {
  param(
    [string]$AppImagePath,
    [string]$AppImageName,
    [string]$JpackageInput
  )

  Write-Host "正在使用便携目录方式生成 Windows 程序镜像。"
  New-Item -ItemType Directory -Path $AppImagePath -Force | Out-Null
  $appDir = Join-Path $AppImagePath "app"
  $runtimeDir = Join-Path $AppImagePath "runtime"
  Copy-DirectoryContents -Source $JpackageInput -Destination $appDir -Description "便携程序输入目录"
  Copy-DirectoryContents -Source $env:JAVA_HOME -Destination $runtimeDir -Description "Java 运行时目录"

  $launcher = @"
@echo off
setlocal
set "APP_HOME=%~dp0"
set "JAVA_EXE=%APP_HOME%runtime\bin\javaw.exe"
if not exist "%JAVA_EXE%" set "JAVA_EXE=%APP_HOME%runtime\bin\java.exe"
if not exist "%JAVA_EXE%" (
  echo 未找到内置 Java 运行时，请完整解压程序包后再运行。
  pause
  exit /b 1
)
start "" "%JAVA_EXE%" -Dfile.encoding=UTF-8 -cp "%APP_HOME%app\*" awa.uxu.douyin.autospark.DouyinAutoSparkApp
"@
  [System.IO.File]::WriteAllText((Join-Path $AppImagePath "$AppImageName.bat"), $launcher, [System.Text.UTF8Encoding]::new($false))

  $consoleLauncher = @"
@echo off
setlocal
set "APP_HOME=%~dp0"
set "JAVA_EXE=%APP_HOME%runtime\bin\java.exe"
if not exist "%JAVA_EXE%" (
  echo 未找到内置 Java 运行时，请完整解压程序包后再运行。
  pause
  exit /b 1
)
"%JAVA_EXE%" -Dfile.encoding=UTF-8 -cp "%APP_HOME%app\*" awa.uxu.douyin.autospark.DouyinAutoSparkApp
"@
  [System.IO.File]::WriteAllText((Join-Path $AppImagePath "$AppImageName-控制台诊断.bat"), $consoleLauncher, [System.Text.UTF8Encoding]::new($false))
}

function Invoke-PlaywrightInstall {
  param([string]$BrowserInstallDir)

  $chrome = $null
  if (Test-Path -LiteralPath $BrowserInstallDir) {
    $chrome = Get-ChildItem -LiteralPath $BrowserInstallDir -Recurse -Filter "chrome.exe" -File | Select-Object -First 1
  }
  if ($chrome) {
    Write-Host "已发现可打包的内置 Chromium 浏览器：$($chrome.FullName)"
    return
  }

  Write-Host "正在下载并准备内置 Chromium 浏览器..."
  if (-not (Test-Path -LiteralPath $BrowserInstallDir)) {
    New-Item -ItemType Directory -Path $BrowserInstallDir | Out-Null
  }
  $lockPath = Join-Path $BrowserInstallDir "__dirlock"
  if (Test-Path -LiteralPath $lockPath) {
    Remove-Item -LiteralPath $lockPath -Force
  }
  $env:PLAYWRIGHT_BROWSERS_PATH = $BrowserInstallDir
  & mvn -q exec:java "-Dexec.mainClass=com.microsoft.playwright.CLI" "-Dexec.args=install chromium"
  if ($LASTEXITCODE -ne 0) {
    throw "内置 Chromium 浏览器下载失败"
  }

  $chrome = Get-ChildItem -LiteralPath $BrowserInstallDir -Recurse -Filter "chrome.exe" -File | Select-Object -First 1
  if (-not $chrome) {
    throw "未找到已下载的 chrome.exe"
  }
  Write-Host "内置 Chromium 浏览器已准备完成：$($chrome.FullName)"
}

if (Test-Path -LiteralPath $pluginDir) {
  try {
    Get-ChildItem -LiteralPath $pluginDir -Force | Remove-Item -Recurse -Force -ErrorAction Stop
  } catch {
    throw "无法清空 PLUGIN 目录。请先关闭从 PLUGIN 目录启动的旧版抖音自动续火花助手及其浏览器，再重新构建。被占用详情：$($_.Exception.Message)"
  }
} else {
  New-Item -ItemType Directory -Path $pluginDir | Out-Null
}

$nextVersion = Get-NextVersion
Write-Host "准备构建无人值守程序，版本：$nextVersion"
Set-JsonVersion -Path $versionFile -Version $nextVersion
Set-PluginYamlVersion -Path $pluginYaml -Version $nextVersion
Set-PomVersion -Path $pomPath -Version $nextVersion
Assert-VersionSync -Version $nextVersion

New-Item -ItemType Directory -Path $buildDir -Force | Out-Null
Write-Host "正在执行 Maven 测试和打包..."
& mvn -DskipTests=false package
if ($LASTEXITCODE -ne 0) {
  throw "Maven 测试或打包失败"
}

Invoke-PlaywrightInstall -BrowserInstallDir $bundledBrowsersDir

$jpackageCommand = Get-Command jpackage -ErrorAction Stop
$javaCommand = Join-Path (Split-Path -Parent $jpackageCommand.Source) "java.exe"
if (-not (Test-Path -LiteralPath $javaCommand -PathType Leaf)) {
  throw "未找到与 jpackage 同目录的 java.exe：$javaCommand"
}
$jpackageRoot = Join-Path $buildDir "jpackage"
$jpackageInput = Join-Path $buildDir "jpackage-input"
$appImageName = "抖音自动续火花助手"
$appImagePath = Join-Path $jpackageRoot $appImageName
if (Test-Path -LiteralPath $jpackageRoot) {
  Remove-Item -LiteralPath $jpackageRoot -Recurse -Force
}
if (Test-Path -LiteralPath $jpackageInput) {
  Remove-Item -LiteralPath $jpackageInput -Recurse -Force
}
New-Item -ItemType Directory -Path $jpackageRoot | Out-Null
New-Item -ItemType Directory -Path $jpackageInput | Out-Null

$mainJar = "douyin-auto-spark-$nextVersion.jar"
Get-ChildItem -LiteralPath (Join-Path $root "target") -Filter "*.jar" -File | Where-Object {
  $_.Name -eq $mainJar -or $_.Name -notlike "douyin-auto-spark-*.jar"
} | ForEach-Object {
  Copy-Item -LiteralPath $_.FullName -Destination $jpackageInput -Force
}
Write-Host "正在生成 Windows 程序镜像..."
$jpackageArgs = @(
  "--verbose",
  "--type", "app-image",
  "--name", $appImageName,
  "--app-version", $nextVersion,
  "--vendor", "hyx",
  "--input", $jpackageInput,
  "--main-jar", $mainJar,
  "--main-class", "awa.uxu.douyin.autospark.DouyinAutoSparkApp",
  "--dest", $jpackageRoot,
  "--java-options", "-Dfile.encoding=UTF-8"
)
Write-Host "jpackage 路径：$($jpackageCommand.Source)"
Write-Host "jpackage 参数：$($jpackageArgs -join ' ')"
$jpackageModuleArgs = @("--module", "jdk.jpackage/jdk.jpackage.main.Main") + $jpackageArgs
$jpackageExitCode = Invoke-NativeProcess -FilePath $javaCommand -Arguments $jpackageModuleArgs
if ($jpackageExitCode -ne 0) {
  Write-Host "jpackage 生成程序失败，将改用便携目录方式继续构建。"
  if (Test-Path -LiteralPath $appImagePath) {
    Remove-Item -LiteralPath $appImagePath -Recurse -Force
  }
  New-PortableAppImage -AppImagePath $appImagePath -AppImageName $appImageName -JpackageInput $jpackageInput
}

$readme = @"
抖音自动续火花助手 $nextVersion

使用方式：
1. 必须先完整解压整个“抖音自动续火花助手”文件夹，不能只复制 exe，也不要直接在压缩包里运行 exe。
2. 双击“抖音自动续火花助手.exe”启动程序；如果没有窗口，请双击“启动诊断.cmd”查看中文提示。
3. 首次使用请在左侧选择账号，再点击“打开登录/聊天页”完成当前账号登录；成功后保持程序运行至少 15 秒，新增账号拥有独立登录态。
4. 程序已包含 Chromium 浏览器本体，不需要另外安装浏览器。
5. 程序会定期加固登录态并复用账号专用浏览器目录：%APPDATA%\DouyinAutoSpark\accounts\账号ID\browser-profile。
6. 配置、状态、日志、失败截图均保存在：%APPDATA%\DouyinAutoSpark。
7. 程序不会绕过验证码、风控或登录校验；登录失效时请重新登录。
8. 服务器部署时，请先在可交互桌面完成登录，再在程序配置中启用“服务器无界面模式”。
9. 无界面模式不依赖浏览器窗口置前、页面刷新或持续可见，并会在浏览器异常后自动恢复。
10. 每个账号的配置、状态、浏览器资料和失败截图独立保存在 accounts\账号ID 目录；归档账号不会直接删除数据。
11. 程序不支持多开；重复启动会显示中文提示，避免多个实例争用同一浏览器登录目录。
12. 定时模式与间隔模式可独立或同时启用；自动触发会随机延迟 0–5 分钟，间隔从上一轮完成后重新计算。
13. 失败会话默认 60 分钟后独立重试一次；程序主动跳过不算失败，结果不确定时会先核验原消息。
14. 发送采用真实鼠标、滚轮和逐字键盘输入；只有检测到正确会话中的新增己方消息气泡才确认成功。
15. 仿人节奏不保证规避平台限制，程序不会绕过登录、验证码或安全验证。
"@
[System.IO.File]::WriteAllText((Join-Path $appImagePath "使用说明.txt"), $readme, [System.Text.UTF8Encoding]::new($false))
$diagnosticCmd = @"
@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo 正在启动抖音自动续火花助手...
echo.
set "启动入口="
if exist "抖音自动续火花助手.exe" set "启动入口=%~dp0抖音自动续火花助手.exe"
if not defined 启动入口 if exist "抖音自动续火花助手.bat" set "启动入口=%~dp0抖音自动续火花助手.bat"
if not defined 启动入口 (
  echo 未找到 抖音自动续火花助手.exe 或 抖音自动续火花助手.bat。请完整解压整个文件夹后再运行。
  pause
  exit /b 1
)
if not exist "app\douyin-auto-spark-$nextVersion.jar" (
  echo 未找到主程序 app\douyin-auto-spark-$nextVersion.jar。
  echo 请不要只复制 exe，请完整解压新版程序包。
  pause
  exit /b 1
)
if not exist "browsers" (
  echo 未找到内置浏览器目录 browsers。
  echo 请完整解压新版程序包，不要直接在压缩包里运行。
  pause
  exit /b 1
)
start "" "%启动入口%"
echo 已发出启动请求。如果窗口仍未出现，请查看日志目录：
echo %APPDATA%\DouyinAutoSpark\logs
pause
"@
[System.IO.File]::WriteAllText((Join-Path $appImagePath "启动诊断.cmd"), $diagnosticCmd, [System.Text.UTF8Encoding]::new($false))
Copy-Item -LiteralPath $bundledBrowsersDir -Destination (Join-Path $appImagePath "browsers") -Recurse -Force

$zipPath = Join-Path $pluginDir "douyin-auto-spark-$nextVersion-windows.zip"
Compress-Archive -Path $appImagePath -DestinationPath $zipPath -Force

Write-Host "构建完成，版本：$nextVersion"
Write-Host "产物：$zipPath"
