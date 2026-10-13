# 建置正式簽名的 APK，commit 並 push；指定版本時另外更新版本號並發布 GitHub Release。
#   release.bat                     建置 + commit + push
#   release.bat 1.5.0 "更新內容"    更新版本號 + 建置 + commit + push + 發布 Release v1.5.0
param(
    [string]$Version = "",
    [string]$Notes = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$repo = "agenga0320/JapaneseMajongPointCalculator"
$gradleFile = Join-Path $root "app\build.gradle.kts"

function Fail($msg) {
    Write-Host ""
    Write-Host "[錯誤] $msg" -ForegroundColor Red
    exit 1
}

function Confirm($question) {
    $ans = Read-Host "$question (Y/N)"
    return $ans -match '^[Yy]'
}

# ---- 環境 ----
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr" }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA "Android\Sdk" }
if (-not (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) { Fail "找不到 JDK：$env:JAVA_HOME（請設定 JAVA_HOME）" }
if (-not (Test-Path $env:ANDROID_HOME)) { Fail "找不到 Android SDK：$env:ANDROID_HOME（請設定 ANDROID_HOME）" }
if (-not (Test-Path (Join-Path $root "keystore.properties"))) {
    Write-Host "[警告] 沒有 keystore.properties，APK 不會簽名，無法覆蓋安裝。" -ForegroundColor Yellow
    if (-not (Confirm "仍要繼續嗎？")) { exit 1 }
}

# ---- 版本號 ----
$gradle = [IO.File]::ReadAllText($gradleFile)
if ($Version) {
    if ($Version -notmatch '^\d+\.\d+\.\d+$') { Fail "版本號格式應為 X.Y.Z（例如 1.5.0）" }
    git rev-parse -q --verify "refs/tags/v$Version" *> $null
    if ($LASTEXITCODE -eq 0) { Fail "tag v$Version 已存在" }
    $code = [int][regex]::Match($gradle, 'versionCode = (\d+)').Groups[1].Value + 1
    $gradle = $gradle -replace 'versionCode = \d+', "versionCode = $code"
    $gradle = $gradle -replace 'versionName = "[^"]*"', "versionName = `"$Version`""
    [IO.File]::WriteAllText($gradleFile, $gradle)
    Write-Host "版本號更新為 $Version（versionCode $code）" -ForegroundColor Cyan
}
$versionName = [regex]::Match($gradle, 'versionName = "([^"]*)"').Groups[1].Value

# ---- 建置 ----
Write-Host ""
Write-Host "==> 建置 v$versionName ..." -ForegroundColor Cyan
& .\gradlew.bat --no-daemon assembleRelease
if ($LASTEXITCODE -ne 0) { Fail "建置失敗" }

$apkName = "JapaneseMahjongPointCalculator-v$versionName.apk"
$dist = Join-Path $root "dist"
New-Item -ItemType Directory -Force $dist | Out-Null
$apk = Join-Path $dist $apkName
Copy-Item "app\build\outputs\apk\release\app-release.apk" $apk -Force
Write-Host "APK：$apk" -ForegroundColor Green

# ---- commit & push ----
Write-Host ""
$changes = git status --short
if ($changes) {
    Write-Host "==> 變更的檔案：" -ForegroundColor Cyan
    $changes | ForEach-Object { Write-Host "   $_" }
    if (Confirm "要 commit 並 push 嗎？") {
        $msg = if ($Version) { "v$versionName" + $(if ($Notes) { "：$Notes" } else { "" }) } else { Read-Host "commit 訊息" }
        if (-not $msg) { $msg = "update" }
        git add -A
        git commit -m $msg
        if ($LASTEXITCODE -ne 0) { Fail "commit 失敗" }
    }
} else {
    Write-Host "沒有需要 commit 的變更。"
}
git push
if ($LASTEXITCODE -ne 0) { Fail "push 失敗" }

# ---- GitHub Release ----
if ($Version) {
    Write-Host ""
    if (Confirm "要發布 GitHub Release v$versionName 嗎？") {
        $direct = "https://github.com/$repo/releases/download/v$versionName/$apkName"
        $body = "## 👉 [直接下載 APK / APK をダウンロード]($direct)`n`n" +
            "點上面的連結下載後開啟即可覆蓋安裝，紀錄會保留。（v1.2.0 以上可在 App 內直接更新）`n" +
            "上のリンクからダウンロードして開くと上書きインストールされます。（v1.2.0 以降はアプリ内で更新できます）"
        if ($Notes) { $body += "`n`n### 更新內容 / 更新内容`n$Notes" }
        gh release create "v$versionName" $apk --repo $repo --target main --title "v$versionName" --notes $body
        if ($LASTEXITCODE -ne 0) { Fail "發布 Release 失敗" }
        Write-Host ""
        Write-Host "已發布：https://github.com/$repo/releases/tag/v$versionName" -ForegroundColor Green
        Write-Host "APK 直接下載：$direct" -ForegroundColor Green
    }
}

Write-Host ""
Write-Host "完成。" -ForegroundColor Green
