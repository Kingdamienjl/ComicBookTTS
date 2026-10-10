<#
.SYNOPSIS
    Run instrumentation tests on connected Android devices

.DESCRIPTION
    Orchestrates device testing for ComicTTS Android app including device detection,
    verification, test execution, and result reporting.

.PARAMETER DeviceSerial
    Specific device serial to test on (optional, prompts if multiple devices)

.PARAMETER SkipBuild
    Skip building APKs and use existing ones

.PARAMETER Verbose
    Display detailed output during test execution

.EXAMPLE
    .\test-device.ps1
    Run tests with device selection prompt if multiple devices connected

.EXAMPLE
    .\test-device.ps1 -DeviceSerial ABC123XYZ
    Run tests on specific device

.EXAMPLE
    .\test-device.ps1 -SkipBuild
    Run tests without rebuilding APKs
#>

param(
    [string]$DeviceSerial,
    [switch]$SkipBuild,
    [switch]$Verbose
)

# Configuration
$MIN_API_LEVEL = 21  # Lowered from 24 to support older devices
$MIN_BATTERY_PERCENT = 15
$WARN_BATTERY_PERCENT = 30
$MIN_STORAGE_MB = 500

# Colors for output
$ColorSuccess = "Green"
$ColorWarning = "Yellow"
$ColorError = "Red"
$ColorInfo = "Cyan"

# Get Android SDK path from local.properties
function Get-AndroidSdkPath {
    $localProps = Join-Path $PSScriptRoot "..\local.properties"
    if (Test-Path $localProps) {
        $content = Get-Content $localProps
        foreach ($line in $content) {
            if ($line -match '^sdk\.dir=(.+)$') {
                $sdkPath = $matches[1] -replace '\\\\', '\' -replace '\\:', ':'
                return $sdkPath
            }
        }
    }

    # Fallback to common locations
    $commonPaths = @(
        "$env:LOCALAPPDATA\Android\Sdk",
        "$env:ANDROID_HOME",
        "$env:ANDROID_SDK_ROOT"
    )

    foreach ($path in $commonPaths) {
        if ($path -and (Test-Path $path)) {
            return $path
        }
    }

    return $null
}

# Get ADB executable path
function Get-AdbPath {
    $sdkPath = Get-AndroidSdkPath
    if ($sdkPath) {
        $adbPath = Join-Path $sdkPath "platform-tools\adb.exe"
        if (Test-Path $adbPath) {
            return $adbPath
        }
    }

    # Try to find ADB in PATH
    $adbInPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($adbInPath) {
        return $adbInPath.Source
    }

    return $null
}

# Get connected devices
function Get-ConnectedDevices {
    param([string]$AdbPath)

    $output = & $AdbPath devices -l 2>&1
    $devices = @()

    foreach ($line in $output) {
        if ($line -match '^([^\s]+)\s+device\s+(.*)$') {
            $serial = $matches[1]
            $details = $matches[2]

            # Parse device details
            $model = if ($details -match 'model:([^\s]+)') { $matches[1] } else { "Unknown" }
            $device = if ($details -match 'device:([^\s]+)') { $matches[1] } else { "Unknown" }

            # Get API level
            $apiLevelOutput = & $AdbPath -s $serial shell getprop ro.build.version.sdk 2>$null
            $apiLevel = ($apiLevelOutput | Select-Object -First 1).Trim()

            $devices += [PSCustomObject]@{
                Serial = $serial
                Model = $model
                Device = $device
                ApiLevel = $apiLevel
            }
        }
    }

    return $devices
}

# Select target device
function Select-TargetDevice {
    param([array]$Devices)

    if ($Devices.Count -eq 0) {
        Write-Host "`nNo devices connected!" -ForegroundColor $ColorError
        Write-Host "`nPlease:" -ForegroundColor $ColorWarning
        Write-Host "  1. Connect your Android device via USB"
        Write-Host "  2. Enable USB debugging in Developer Options"
        Write-Host "  3. Authorize this computer on your device"
        Write-Host "`nRun '.\scripts\setup-device-testing.ps1' for detailed setup instructions."
        return $null
    }

    if ($Devices.Count -eq 1) {
        return $Devices[0]
    }

    # Multiple devices - prompt for selection
    Write-Host "`nMultiple devices connected:" -ForegroundColor $ColorInfo
    Write-Host ""
    for ($i = 0; $i -lt $Devices.Count; $i++) {
        $device = $Devices[$i]
        Write-Host "  [$($i + 1)] $($device.Model) (API $($device.ApiLevel)) - $($device.Serial)"
    }
    Write-Host ""

    do {
        $selection = Read-Host "Select device number [1-$($Devices.Count)]"
        $index = [int]$selection - 1
    } while ($index -lt 0 -or $index -ge $Devices.Count)

    return $Devices[$index]
}

# Test device capabilities
function Test-DeviceCapabilities {
    param(
        [string]$AdbPath,
        [string]$Serial
    )

    $issues = @()
    $warnings = @()

    # Check API level
    $apiLevelOutput = & $AdbPath -s $Serial shell getprop ro.build.version.sdk 2>$null
    $apiLevel = ($apiLevelOutput | Select-Object -First 1).Trim()
    if ($apiLevel -and [int]$apiLevel -lt $MIN_API_LEVEL) {
        $issues += "API level $apiLevel is below minimum required ($MIN_API_LEVEL)"
    }

    # Check battery level
    $batteryInfo = & $AdbPath -s $Serial shell dumpsys battery 2>$null
    $batteryLevel = 100
    $batteryText = $batteryInfo -join "`n"
    if ($batteryText -match 'level:\s*(\d+)') {
        $batteryLevel = [int]$matches[1]
        if ($batteryLevel -lt $MIN_BATTERY_PERCENT) {
            $issues += "Battery level ${batteryLevel}% is below minimum ($MIN_BATTERY_PERCENT%)"
        } elseif ($batteryLevel -lt $WARN_BATTERY_PERCENT) {
            $warnings += "Battery level is ${batteryLevel}% (consider charging to avoid interruption)"
        }
    }

    # Check available storage
    $storageInfo = & $AdbPath -s $Serial shell df /data 2>$null
    $storageText = $storageInfo -join "`n"
    if ($storageText -match '\s+(\d+)K\s+\d+K\s+(\d+)K') {
        $availableKb = [int]$matches[2]
        $availableMb = [math]::Round($availableKb / 1024, 0)
        if ($availableMb -lt $MIN_STORAGE_MB) {
            $issues += "Available storage ${availableMb}MB is below minimum ($MIN_STORAGE_MB MB)"
        }
    }

    return @{
        Issues = $issues
        Warnings = $warnings
        ApiLevel = $apiLevel
        BatteryLevel = $batteryLevel
    }
}

# Parse test results
function Get-TestResults {
    param([string]$ResultsPath)

    if (-not (Test-Path $ResultsPath)) {
        return $null
    }

    $xmlFiles = Get-ChildItem -Path $ResultsPath -Filter "TEST-*.xml" -ErrorAction SilentlyContinue
    if ($xmlFiles.Count -eq 0) {
        return $null
    }

    $totalTests = 0
    $totalFailures = 0
    $totalSkipped = 0
    $totalTime = 0.0
    $failures = @()

    foreach ($xmlFile in $xmlFiles) {
        try {
            [xml]$xml = Get-Content $xmlFile.FullName
            $testSuite = $xml.testsuite

            $totalTests += [int]$testSuite.tests
            $totalFailures += [int]$testSuite.failures
            $totalSkipped += [int]$testSuite.skipped
            $totalTime += [double]$testSuite.time

            # Collect failure details
            foreach ($testCase in $testSuite.testcase) {
                if ($testCase.failure) {
                    $failures += [PSCustomObject]@{
                        Class = $testCase.classname.Split('.')[-1]
                        Method = $testCase.name
                        Message = $testCase.failure.message
                    }
                }
            }
        } catch {
            Write-Host "Warning: Failed to parse $($xmlFile.Name)" -ForegroundColor $ColorWarning
        }
    }

    return [PSCustomObject]@{
        Total = $totalTests
        Passed = $totalTests - $totalFailures - $totalSkipped
        Failed = $totalFailures
        Skipped = $totalSkipped
        Duration = $totalTime
        Failures = $failures
    }
}

# Main execution
function Start-DeviceTest {
    Write-Host ""
    Write-Host "=== ComicTTS Device Testing ===" -ForegroundColor $ColorInfo
    Write-Host ""

    # Find ADB
    $adbPath = Get-AdbPath
    if (-not $adbPath) {
        Write-Host "ERROR: ADB not found!" -ForegroundColor $ColorError
        Write-Host "Please run '.\scripts\setup-device-testing.ps1' to verify your Android SDK installation."
        exit 1
    }

    if ($Verbose) {
        Write-Host "Using ADB: $adbPath" -ForegroundColor Gray
    }

    # Get connected devices
    Write-Host "Detecting devices..." -ForegroundColor $ColorInfo
    $devices = Get-ConnectedDevices -AdbPath $adbPath

    # Select device
    $selectedDevice = if ($DeviceSerial) {
        $devices | Where-Object { $_.Serial -eq $DeviceSerial } | Select-Object -First 1
        if (-not $selectedDevice) {
            Write-Host "ERROR: Device with serial '$DeviceSerial' not found!" -ForegroundColor $ColorError
            exit 1
        }
        $selectedDevice
    } else {
        Select-TargetDevice -Devices $devices
    }

    if (-not $selectedDevice) {
        exit 1
    }

    Write-Host "Selected: $($selectedDevice.Model) (API $($selectedDevice.ApiLevel))" -ForegroundColor $ColorSuccess
    Write-Host ""

    # Verify device capabilities
    Write-Host "Verifying device capabilities..." -ForegroundColor $ColorInfo
    $capabilities = Test-DeviceCapabilities -AdbPath $adbPath -Serial $selectedDevice.Serial

    if ($capabilities.Issues.Count -gt 0) {
        Write-Host "`nDevice issues detected:" -ForegroundColor $ColorError
        foreach ($issue in $capabilities.Issues) {
            Write-Host "  - $issue" -ForegroundColor $ColorError
        }
        Write-Host "`nCannot proceed with testing." -ForegroundColor $ColorError
        exit 1
    }

    if ($capabilities.Warnings.Count -gt 0) {
        foreach ($warning in $capabilities.Warnings) {
            Write-Host "  Warning: $warning" -ForegroundColor $ColorWarning
        }
    }

    Write-Host "  API Level: $($capabilities.ApiLevel)" -ForegroundColor Gray
    Write-Host "  Battery: $($capabilities.BatteryLevel)%" -ForegroundColor Gray
    Write-Host ""

    # Navigate to project directory
    $projectRoot = Split-Path $PSScriptRoot -Parent
    Push-Location $projectRoot

    try {
        # Build APKs if needed
        if (-not $SkipBuild) {
            Write-Host "Building APKs..." -ForegroundColor $ColorInfo
            $gradleCmd = if (Test-Path ".\gradlew.bat") { ".\gradlew.bat" } else { "gradle" }

            if ($Verbose) {
                & $gradleCmd assembleDebug assembleDebugAndroidTest
            } else {
                & $gradleCmd assembleDebug assembleDebugAndroidTest 2>&1 | Out-Null
            }

            if ($LASTEXITCODE -ne 0) {
                Write-Host "ERROR: Build failed!" -ForegroundColor $ColorError
                Write-Host "Run with -Verbose flag to see detailed build output" -ForegroundColor $ColorWarning
                exit 1
            }
            Write-Host "  Build completed successfully" -ForegroundColor $ColorSuccess
            Write-Host ""
        }

        # Run tests
        Write-Host "Running tests on device..." -ForegroundColor $ColorInfo
        Write-Host ""

        $env:ANDROID_SERIAL = $selectedDevice.Serial
        $gradleCmd = if (Test-Path ".\gradlew.bat") { ".\gradlew.bat" } else { "gradle" }

        $testStart = Get-Date
        if ($Verbose) {
            & $gradleCmd connectedDebugAndroidTest
        } else {
            & $gradleCmd connectedDebugAndroidTest --quiet 2>&1 | Out-Null
        }
        $testEnd = Get-Date
        $testDuration = ($testEnd - $testStart).TotalSeconds

        # Parse results
        $resultsPath = "app\build\outputs\androidTest-results\connected"
        $results = Get-TestResults -ResultsPath $resultsPath

        Write-Host ""
        Write-Host "=== Test Results ===" -ForegroundColor $ColorInfo
        Write-Host ""

        if ($results) {
            Write-Host "Tests completed in $([math]::Round($testDuration, 1))s" -ForegroundColor Gray
            Write-Host ""

            $passedColor = if ($results.Passed -gt 0) { $ColorSuccess } else { $ColorWarning }
            $failedColor = if ($results.Failed -eq 0) { $ColorSuccess } else { $ColorError }

            Write-Host ("Passed: {0,3}" -f $results.Passed) -ForegroundColor $passedColor -NoNewline
            Write-Host " | " -NoNewline
            Write-Host ("Failed: {0,3}" -f $results.Failed) -ForegroundColor $failedColor -NoNewline
            Write-Host " | " -NoNewline
            Write-Host ("Skipped: {0,3}" -f $results.Skipped) -ForegroundColor Gray
            Write-Host ""

            if ($results.Failed -gt 0) {
                Write-Host "Failed Tests:" -ForegroundColor $ColorError
                foreach ($failure in $results.Failures) {
                    Write-Host "  - $($failure.Method)" -ForegroundColor $ColorError
                    if ($failure.Message) {
                        Write-Host "    $($failure.Message)" -ForegroundColor Gray
                    }
                }
                Write-Host ""
            }

            # Show report location
            $reportPath = "app\build\reports\androidTests\connected\index.html"
            if (Test-Path $reportPath) {
                Write-Host "Full report: $reportPath" -ForegroundColor $ColorInfo
            }

            # Exit with appropriate code
            if ($results.Failed -gt 0) {
                exit 1
            }
        } else {
            Write-Host "Could not parse test results." -ForegroundColor $ColorWarning
            Write-Host "Check: $resultsPath" -ForegroundColor $ColorInfo
        }

    } finally {
        Pop-Location
    }
}

# Run main function
Start-DeviceTest
