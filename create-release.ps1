# GitHub Release 创建 + APK 上传脚本
$ErrorActionPreference = "Stop"
$proxy = "http://127.0.0.1:31181"
$repo = "nilgpt2024/Camera-Stream-Monitor"
$apiBase = "https://api.github.com/repos/$repo"

Write-Host "=== GitHub Release Creator ===" -ForegroundColor Cyan
Write-Host ""

# 获取 Token
Write-Host "Please enter your GitHub Personal Access Token:" -ForegroundColor Yellow
Write-Host "(Generate at: https://github.com/settings/tokens)" -ForegroundColor Gray
$token = Read-Host "Token"
if ([string]::IsNullOrWhiteSpace($token)) { Write-Host "No token provided, exit." -ForegroundColor Red; exit 1 }

$headers = @{
    "Accept" = "application/vnd.github+json"
    "Authorization" = "Bearer $token"
    "X-GitHub-Api-Version" = "2022-11-28"
}

# 检查已有 release
Write-Host "[1/3] Checking existing release..." -ForegroundColor Cyan
try {
    $resp = Invoke-RestMethod -Uri "$apiBase/releases/tags/v1.0.0" -Proxy $proxy -Headers $headers -Method Get
    Write-Host "Release v1.0.0 already exists!" -ForegroundColor Yellow
    $releaseId = $resp.id
    $uploadUrl = $resp.upload_url -replace '\{.*\}', ''
} catch {
    if ($_.Exception.Response.StatusCode -eq 404) {
        # 创建新 Release
        Write-Host "[2/3] Creating release v1.0.0..." -ForegroundColor Cyan

        $desc = "## Old Phone Monitor v1.0.0`n`nTurn your old phone into a security camera! No extra equipment needed.`n`n### Features`n- Real-time video streaming (RTMP)`n- Remote viewing from any device`n- Recording and playback`n- WiFi & mobile network support`n- Chinese/English bilingual UI`n`n### Use Cases`nElderly care | Shop security | Pet monitoring | Garage safety | Baby monitor`n`n### Install`n1. Download APK below`n2. Enable unknown sources in settings`n3. Install APK file`n4. Grant camera/mic/storage permissions`n`n> Debug version."

        $createJson = "{""tag_name"":""v1.0.0"",""target_commitish"":""main"",""name"":""Old Phone Monitor v1.0.0"",""body"":""$desc"",""draft"":false,""prerelease"":false}"

        $resp = Invoke-RestMethod -Uri "$apiBase/releases" -Proxy $proxy `
            -Method Post -Headers $headers -ContentType "application/json; charset=utf-8" -Body ([System.Text.Encoding]::UTF8.GetBytes($createJson))
        $releaseId = $resp.id
        $uploadUrl = $resp.upload_url -replace '\{.*\}', ''
        Write-Host "Release created! ID=$releaseId" -ForegroundColor Green
        Write-Host "URL: $($resp.html_url)" -ForegroundColor Green
    } else {
        Write-Host "API Error: $_" -ForegroundColor Red; exit 1
    }
}

# 上传 APK
Write-Host ""
Write-Host "[3/3] Uploading APK..." -ForegroundColor Cyan
$apkPath = "F:\android\andwin\docs\old-phone-monitor-v1.0.0-debug.apk"
if (-not (Test-Path $apkPath)) {
    # try Chinese name
    $apkPath = "F:\android\andwin\docs\旧手机监控助手-v1.0.0-debug.apk"
}
if (-not (Test-Path $apkPath)) { Write-Host "APK not found!" -ForegroundColor Red; exit 1 }

$apkSize = [math]::Round((Get-Item $apkPath).Length / 1MB, 2)
Write-Host "APK size: $apkSize MB"

$headersUpload = @{
    "Accept" = "application/vnd.github+json"
    "Authorization" = "Bearer $token"
    "X-GitHub-Api-Version" = "2022-11-28"
    "Content-Type" = "application/vnd.android.package-archive"
}

$uploadUrlFull = "${uploadUrl}?name=old-phone-monitor-v1.0.0-debug.apk"
Write-Host "Uploading..."

try {
    $uploadResp = Invoke-RestMethod -Uri $uploadUrlFull -Proxy $proxy `
        -Method Post -Headers $headersUpload -InFile $apkPath
    Write-Host ""
    Write-Host "=== SUCCESS! ===" -ForegroundColor Green
    Write-Host "Download: $($uploadResp.browser_download_url)" -ForegroundColor Cyan
} catch {
    Write-Host "Upload error: $_" -ForegroundColor Red
    exit 1
}
