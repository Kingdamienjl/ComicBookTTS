<#
.SYNOPSIS
    Setup and verify Android device testing environment

.DESCRIPTION
    Verifies that all prerequisites for device testing are met including Android SDK,
    ADB, device connection, and project configuration.

.EXAMPLE
    .\setup-device-testing.ps1
    Run environment verification and setup
#>

# Colors
$ColorSuccess = "Green"
$ColorWarning = "Yellow"
$ColorError = "Red"
$ColorInfo = "Cyan"

Write-Host ""
Write-Host "=== ComicTTS Device Testing Setup ===" -ForegroundColor $ColorInfo
Write-Host ""

$allChecks = @()

# Check 1: Verify local.properties exists
Write-Host "[1/6] Checking local.properties..." -ForegroundColor $ColorInfo
$localProps = Join-Path $PSScriptRoot "..\local.properties"
if (Test-Path $localProps) {
    Write-Host "  Found" -ForegroundColor $ColorSuccess
    $allChecks += $true
} else {
    Write-Host "  NOT FOUND" -ForegroundColor $ColorError
    Write-Host "  Create local.properties with your Android SDK path" -ForegroundColor $ColorWarning
    $allChecks += $false
}
Write-Host ""

# Check 2: Verify Android SDK
Write-Host "[2/6] Checking Android SDK..." -ForegroundColor $ColorInfo
$sdkPath = $null

if (Test-Path $localProps) {
    $content = Get-Content $localProps
    foreach ($line in $content) {
        if ($line -match '^sdk\.dir=(.+)$') {
            $sdkPath = $matches[1] -replace '\\\\', '\' -replace '\\:', ':'
            break
        }
    }
}

if (-not $sdkPath) {
    # Try environment variables
    if ($env:ANDROID_HOME) {
        $sdkPath = $env:ANDROID_HOME
    } elseif ($env:ANDROID_SDK_ROOT) {
        $sdkPath = $env:ANDROID_SDK_ROOT
    } else {
        $sdkPath = "$env:LOCALAPPDATA\Android\Sdk"
    }
}

if (Test-Path $sdkPath) {
    Write-Host "  Path: $sdkPath" -ForegroundColor $ColorSuccess
    $allChecks += $true
} else {
    Write-Host "  NOT FOUND at: $sdkPath" -ForegroundColor $ColorError
    Write-Host "  Install Android Studio or Android SDK Command-line Tools" -ForegroundColor $ColorWarning
    Write-Host "  https://developer.android.com/studio" -ForegroundColor $ColorInfo
    $allChecks += $false
}
Write-Host ""

# Check 3: Verify ADB
Write-Host "[3/6] Checking ADB (Android Debug Bridge)..." -ForegroundColor $ColorInfo
$adbPath = $null

if ($sdkPath) {
    $adbPath = Join-Path $sdkPath "platform-tools\adb.exe"
}

if ($adbPath -and (Test-Path $adbPath)) {
    Write-Host "  Path: $adbPath" -ForegroundColor $ColorSuccess

    # Get ADB version
    $versionOutput = & $adbPath version 2>&1 | Select-Object -First 1
    Write-Host "  $versionOutput" -ForegroundColor Gray
    $allChecks += $true
} else {
    Write-Host "  NOT FOUND" -ForegroundColor $ColorError
    Write-Host "  Install Android SDK Platform-Tools" -ForegroundColor $ColorWarning
    $allChecks += $false
    $adbPath = $null
}
Write-Host ""

# Check 4: Test ADB connectivity
if ($adbPath) {
    Write-Host "[4/6] Testing ADB server..." -ForegroundColor $ColorInfo

    # Start ADB server if needed
    & $adbPath start-server 2>&1 | Out-Null
    Write-Host "  ADB server started" -ForegroundColor $ColorSuccess
    $allChecks += $true
    Write-Host ""
} else {
    Write-Host "[4/6] Skipping ADB server test (ADB not found)" -ForegroundColor $ColorWarning
    $allChecks += $false
    Write-Host ""
}

# Check 5: Detect connected devices
if ($adbPath) {
    Write-Host "[5/6] Detecting connected devices..." -ForegroundColor $ColorInfo

    $devicesOutput = & $adbPath devices -l 2>&1
    $deviceLines = $devicesOutput | Where-Object { $_ -match '^\S+\s+device\s' }

    if ($deviceLines) {
        Write-Host "  Found $($deviceLines.Count) device(s):" -ForegroundColor $ColorSuccess
        foreach ($line in $deviceLines) {
            if ($line -match '^([^\s]+)\s+device\s+(.*)$') {
                $serial = $matches[1]
                $details = $matches[2]

                $model = if ($details -match 'model:([^\s]+)') { $matches[1] } else { "Unknown" }
                $apiLevelOutput = & $adbPath -s $serial shell getprop ro.build.version.sdk 2>$null
                $apiLevel = ($apiLevelOutput | Select-Object -First 1).Trim()

                Write-Host "    - $model (API $apiLevel) - Serial: $serial" -ForegroundColor Gray

                # Check USB debugging
                $debugCheck = & $adbPath -s $serial shell getprop ro.debuggable 2>$null
                if ($debugCheck -match '1') {
                    Write-Host "      USB Debugging: Enabled" -ForegroundColor $ColorSuccess
                }
            }
        }
        $allChecks += $true
    } else {
        Write-Host "  No devices detected" -ForegroundColor $ColorWarning
        Write-Host ""
        Write-Host "  To connect a device:" -ForegroundColor $ColorInfo
        Write-Host "    1. Connect your Android device via USB cable"
        Write-Host "    2. Enable Developer Options on your device:"
        Write-Host "       Settings > About Phone > Tap 'Build Number' 7 times"
        Write-Host "    3. Enable USB Debugging:"
        Write-Host "       Settings > System > Developer Options > USB Debugging"
        Write-Host "    4. Authorize this computer when prompted on your device"
        Write-Host ""
        Write-Host "  Then run this script again to verify the connection."
        $allChecks += $false
    }
} else {
    Write-Host "[5/6] Skipping device detection (ADB not found)" -ForegroundColor $ColorWarning
    $allChecks += $false
}
Write-Host ""

# Check 6: Verify project structure
Write-Host "[6/6] Checking project structure..." -ForegroundColor $ColorInfo
$projectRoot = Split-Path $PSScriptRoot -Parent
$buildGradle = Join-Path $projectRoot "app\build.gradle"
$gradlewBat = Join-Path $projectRoot "gradlew.bat"

$projectChecks = @()

if (Test-Path $buildGradle) {
    Write-Host "  app/build.gradle: Found" -ForegroundColor $ColorSuccess
    $projectChecks += $true
} else {
    Write-Host "  app/build.gradle: NOT FOUND" -ForegroundColor $ColorError
    $projectChecks += $false
}

if (Test-Path $gradlewBat) {
    Write-Host "  gradlew.bat: Found" -ForegroundColor $ColorSuccess
    $projectChecks += $true
} else {
    Write-Host "  gradlew.bat: NOT FOUND" -ForegroundColor $ColorError
    $projectChecks += $false
}

# Check for instrumentation tests
$instrumentationTest = Join-Path $projectRoot "app\src\androidTest\java\com\example\comicbooktts\ReaderActivityInstrumentationTest.kt"
if (Test-Path $instrumentationTest) {
    Write-Host "  Instrumentation tests: Found" -ForegroundColor $ColorSuccess
    $projectChecks += $true
} else {
    Write-Host "  Instrumentation tests: NOT FOUND" -ForegroundColor $ColorWarning
    $projectChecks += $false
}

$allChecks += ($projectChecks | Where-Object { $_ -eq $true }).Count -eq $projectChecks.Count

Write-Host ""

# Create test report directories
$reportDirs = @(
    (Join-Path $projectRoot "test-reports\device-tests"),
    (Join-Path $projectRoot "test-results\device-tests")
)

foreach ($dir in $reportDirs) {
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
}

# Summary
Write-Host "=== Setup Summary ===" -ForegroundColor $ColorInfo
Write-Host ""

$passedChecks = ($allChecks | Where-Object { $_ -eq $true }).Count
$totalChecks = $allChecks.Count

if ($passedChecks -eq $totalChecks) {
    Write-Host "All checks passed! ($passedChecks/$totalChecks)" -ForegroundColor $ColorSuccess
    Write-Host ""
    Write-Host "You're ready to run device tests:" -ForegroundColor $ColorSuccess
    Write-Host "  .\scripts\test-device.ps1" -ForegroundColor $ColorInfo
    Write-Host ""
    Write-Host "For more information, see:" -ForegroundColor $ColorInfo
    Write-Host "  .\DEVICE_TESTING.md" -ForegroundColor $ColorInfo
} else {
    Write-Host "Checks passed: $passedChecks/$totalChecks" -ForegroundColor $ColorWarning
    Write-Host ""
    Write-Host "Please resolve the issues above before running device tests." -ForegroundColor $ColorWarning

    if (-not (Test-Path $localProps)) {
        Write-Host ""
        Write-Host "Quick fix for missing local.properties:" -ForegroundColor $ColorInfo
        Write-Host "  Create the file with this content:" -ForegroundColor $ColorInfo
        Write-Host "  sdk.dir=C:\\Users\\$env:USERNAME\\AppData\\Local\\Android\\Sdk" -ForegroundColor Gray
    }
}

Write-Host ""
