<#
.SYNOPSIS
    Automated build, install, and test script for ComicBookTTS

.DESCRIPTION
    Builds the APK, installs it on connected device, launches the app,
    and optionally opens a comic file for testing.

.PARAMETER SkipBuild
    Skip building and just install/launch

.PARAMETER ComicFile
    Path to a comic file on the device to open automatically

.PARAMETER WatchLogs
    Watch device logs after launching

.EXAMPLE
    .\auto-test.ps1
    .\auto-test.ps1 -SkipBuild
    .\auto-test.ps1 -ComicFile "/sdcard/Download/comic.cbr"
    .\auto-test.ps1 -WatchLogs
#>

param(
    [switch]$SkipBuild,
    [string]$ComicFile,
    [switch]$WatchLogs
)

$ErrorActionPreference = "Stop"

# Configuration
$ADB = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$PACKAGE = "com.example.comicbooktts.debug"
$MAIN_ACTIVITY = "com.example.comicbooktts.MainActivity"
$APK_PATH = "app\build\outputs\apk\debug\app-debug.apk"

Write-Host "`n=== ComicBookTTS Auto-Test ===" -ForegroundColor Cyan

# Check ADB
if (-not (Test-Path $ADB)) {
    Write-Host "ERROR: ADB not found at $ADB" -ForegroundColor Red
    exit 1
}

# Check device
Write-Host "`nChecking device connection..." -ForegroundColor Yellow
$devices = & $ADB devices | Select-String "device$"
if (-not $devices) {
    Write-Host "ERROR: No device connected" -ForegroundColor Red
    exit 1
}
Write-Host "  Device found" -ForegroundColor Green

# Build
if (-not $SkipBuild) {
    Write-Host "`nBuilding APK..." -ForegroundColor Yellow
    & .\gradlew assembleDebug --quiet
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Build failed" -ForegroundColor Red
        exit 1
    }
    Write-Host "  Build successful" -ForegroundColor Green
}

# Install
Write-Host "`nInstalling APK..." -ForegroundColor Yellow
& $ADB install -r $APK_PATH 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Install failed" -ForegroundColor Red
    exit 1
}
Write-Host "  Install successful" -ForegroundColor Green

# Clear logs
Write-Host "`nClearing device logs..." -ForegroundColor Yellow
& $ADB logcat -c

# Force stop any existing instance
Write-Host "Stopping existing app..." -ForegroundColor Yellow
& $ADB shell am force-stop $PACKAGE 2>&1 | Out-Null

# Launch app
Write-Host "`nLaunching app..." -ForegroundColor Yellow
if ($ComicFile) {
    # Launch with comic file intent
    Write-Host "  Opening comic: $ComicFile" -ForegroundColor Cyan
    $uri = "file://$ComicFile"
    & $ADB shell am start -n "$PACKAGE/$MAIN_ACTIVITY" -a android.intent.action.VIEW -d $uri -t "application/x-cbr"
} else {
    # Normal launch
    & $ADB shell am start -n "$PACKAGE/$MAIN_ACTIVITY"
}
Write-Host "  App launched" -ForegroundColor Green

# Wait for app to start
Start-Sleep -Seconds 2

# Check if app is running
$running = & $ADB shell "ps -A | grep $PACKAGE" 2>&1
if ($running) {
    Write-Host "`nApp is running" -ForegroundColor Green
} else {
    Write-Host "`nWARNING: App may have crashed" -ForegroundColor Red
    Write-Host "`nRecent crash logs:" -ForegroundColor Yellow
    & $ADB logcat -d -s AndroidRuntime:E | Select-Object -Last 20
}

# Watch logs if requested
if ($WatchLogs) {
    Write-Host "`nWatching logs (Ctrl+C to stop)..." -ForegroundColor Yellow
    & $ADB logcat -v time "*:S" "ComicTTS:V" "DualEngineOCR:V" "ReaderActivity:V" "AndroidRuntime:E"
}

Write-Host "`n=== Done ===" -ForegroundColor Cyan
