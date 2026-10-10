# Physical Device Testing Guide

Complete guide for running ComicTTS instrumentation tests on physical Android devices.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Initial Setup](#initial-setup)
- [Running Tests](#running-tests)
- [Understanding Results](#understanding-results)
- [Troubleshooting](#troubleshooting)
- [Advanced Usage](#advanced-usage)

## Prerequisites

### Hardware Requirements

- **Android Device**: Running Android 7.0 (API 24) or higher
- **USB Cable**: For connecting device to computer
- **Computer**: Windows machine with Android SDK installed

### Software Requirements

- **Android SDK**: Installed and configured (usually via Android Studio)
- **ADB (Android Debug Bridge)**: Included with Android SDK platform-tools
- **PowerShell**: Pre-installed on Windows
- **USB Debugging**: Enabled on your Android device

### Device Preparation

#### Enable Developer Options

1. Open **Settings** on your Android device
2. Navigate to **About Phone** (or **About Device**)
3. Find **Build Number**
4. Tap **Build Number** 7 times rapidly
5. You'll see a message: "You are now a developer!"

#### Enable USB Debugging

1. Go back to main **Settings**
2. Navigate to **System** > **Developer Options**
   - On some devices: **Settings** > **Developer Options**
3. Scroll down and enable **USB Debugging**
4. Tap **OK** when prompted about allowing USB debugging

#### Authorize Your Computer

1. Connect your device to your computer via USB
2. A prompt will appear on your device: "Allow USB debugging?"
3. Check "Always allow from this computer"
4. Tap **OK**

## Initial Setup

Run the setup script to verify your environment is configured correctly:

```powershell
cd ComicBookTTS
.\scripts\setup-device-testing.ps1
```

### What the Setup Script Checks

1. **local.properties**: Verifies Android SDK path is configured
2. **Android SDK**: Confirms SDK installation exists
3. **ADB**: Checks that Android Debug Bridge is available
4. **ADB Server**: Starts the ADB server if needed
5. **Device Connection**: Detects connected devices
6. **Project Structure**: Verifies required files exist

### Expected Output

```
=== ComicTTS Device Testing Setup ===

[1/6] Checking local.properties...
  ✓ Found

[2/6] Checking Android SDK...
  ✓ Path: C:\Users\YourName\AppData\Local\Android\Sdk

[3/6] Checking ADB (Android Debug Bridge)...
  ✓ Path: C:\Users\YourName\AppData\Local\Android\Sdk\platform-tools\adb.exe
  Android Debug Bridge version 1.0.41

[4/6] Testing ADB server...
  ✓ ADB server started

[5/6] Detecting connected devices...
  ✓ Found 1 device(s):
    - Galaxy S21 (API 33) - Serial: ABC123XYZ
      USB Debugging: ✓ Enabled

[6/6] Checking project structure...
  ✓ app/build.gradle: Found
  ✓ gradlew.bat: Found
  ✓ Instrumentation tests: Found

=== Setup Summary ===

✓ All checks passed! (6/6)

You're ready to run device tests:
  .\scripts\test-device.ps1
```

## Running Tests

### Method 1: PowerShell Script (Recommended)

The PowerShell script provides the best experience with multi-device support and detailed output:

```powershell
# Run tests with device selection
.\scripts\test-device.ps1

# Run on specific device (skip selection prompt)
.\scripts\test-device.ps1 -DeviceSerial ABC123XYZ

# Skip building APKs (faster if already built)
.\scripts\test-device.ps1 -SkipBuild

# Verbose output (see detailed Gradle output)
.\scripts\test-device.ps1 -Verbose
```

### Method 2: Gradle Tasks

Use Gradle directly for simpler execution:

```powershell
# Run full device test suite
.\gradlew deviceTest

# Run connected Android tests (standard)
.\gradlew connectedDebugAndroidTest

# Quick test without building
.\gradlew quickDeviceTest
```

### Test Execution Flow

When you run tests, here's what happens:

1. **Device Detection**: Script finds all connected devices
2. **Device Selection**: Prompts you to choose if multiple devices are connected
3. **Capability Check**: Verifies device meets requirements (API level, battery, storage)
4. **Build APKs**: Compiles app and test APKs (unless `-SkipBuild` is used)
5. **Installation**: Installs both APKs to the selected device
6. **Test Execution**: Runs all instrumentation tests
7. **Result Collection**: Gathers test results and parses them
8. **Report Generation**: Creates summary report

### Example Test Run

```
=== ComicTTS Device Testing ===

Detecting devices...
Selected: Galaxy S21 (API 33)

Verifying device capabilities...
  API Level: 33
  Battery: 75%

Building APKs...
  ✓ Build completed successfully

Running tests on device...

=== Test Results ===

Tests completed in 47.3s

✓ Passed:  23 | ✗ Failed:   0 | ⊘ Skipped:   0

Full report: app\build\reports\androidTests\connected\index.html
```

## Understanding Results

### Console Output

The script displays a summary with:

- **Test Duration**: Total time to run all tests
- **Passed Count**: Number of tests that succeeded
- **Failed Count**: Number of tests that failed
- **Skipped Count**: Number of tests that were skipped

### Failed Test Details

If tests fail, you'll see detailed information:

```
✗ Failed: 2

Failed Tests:
  - testRotationHandling
    java.lang.AssertionError: Expected view visible
  - testZoomGesture
    androidx.test.espresso.NoMatchingViewException: No views found
```

### HTML Reports

Detailed HTML reports are generated at:

```
app\build\reports\androidTests\connected\index.html
```

Open this file in your browser to see:

- Full test execution details
- Stack traces for failures
- Test duration breakdown
- Device information

### Test Result Files

Raw test results in JUnit XML format:

```
app\build\outputs\androidTest-results\connected\TEST-*.xml
```

These can be parsed by CI/CD systems or test reporting tools.

## Troubleshooting

### Device Not Detected

**Problem**: "No devices connected" error

**Solutions**:

1. Check USB cable is properly connected
2. Try a different USB port (use USB 2.0 if 3.0 causes issues)
3. Unlock your device screen
4. Check USB debugging is enabled: Settings > Developer Options
5. Revoke and re-authorize USB debugging:
   - Settings > Developer Options > Revoke USB debugging authorizations
   - Reconnect device and re-authorize
6. Restart ADB server:
   ```powershell
   adb kill-server
   adb start-server
   adb devices
   ```

### Device Shows as "Unauthorized"

**Problem**: Device appears in `adb devices` but shows "unauthorized"

**Solution**:

1. Check your device screen for authorization prompt
2. Make sure device is unlocked
3. Check "Always allow from this computer"
4. Tap OK/Allow

### Build Failures

**Problem**: Gradle build fails

**Solutions**:

1. Clean and rebuild:
   ```powershell
   .\gradlew clean
   .\gradlew build
   ```

2. Check Java version (requires Java 17+):
   ```powershell
   java -version
   ```

3. Sync Gradle files in Android Studio
4. Check `local.properties` has correct SDK path

### Installation Failures

**Problem**: APK installation fails

**Solutions**:

1. Uninstall existing app:
   ```powershell
   adb uninstall com.example.comicbooktts.debug
   adb uninstall com.example.comicbooktts.debug.test
   ```

2. Check device storage (needs at least 500MB free)
3. Disable "Verify apps over USB" in Developer Options

### Test Execution Failures

**Problem**: Tests start but fail or hang

**Solutions**:

1. Ensure device screen stays on during tests:
   - Settings > Developer Options > Stay awake (when charging)

2. Disable animations (recommended for testing):
   - Settings > Developer Options > Window animation scale: Off
   - Settings > Developer Options > Transition animation scale: Off
   - Settings > Developer Options > Animator duration scale: Off

3. Keep device unlocked during test execution

4. Close other apps to free up memory

### Low Battery Warning

**Problem**: "Battery level too low" error

**Solution**:

- Charge device to at least 15% (30%+ recommended)
- Connect charger during test execution
- Tests can drain battery quickly due to screen activity

### ADB Not Found

**Problem**: "ADB not found" error

**Solutions**:

1. Install Android SDK Platform-Tools
2. Add platform-tools to system PATH:
   - Windows: Add `C:\Users\YourName\AppData\Local\Android\Sdk\platform-tools` to PATH
3. Verify installation:
   ```powershell
   adb version
   ```

### Multiple Devices Connected

**Problem**: Have multiple devices but want to test on specific one

**Solution**:

Use the `-DeviceSerial` parameter:

```powershell
# List devices first
adb devices -l

# Run on specific device
.\scripts\test-device.ps1 -DeviceSerial ABC123XYZ
```

## Advanced Usage

### Running Specific Tests

Use Gradle's test filtering:

```powershell
# Run specific test class
.\gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.comicbooktts.ReaderActivityInstrumentationTest

# Run specific test method
.\gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.comicbooktts.ReaderActivityInstrumentationTest#testBasicPageNavigation
```

### Collecting Logcat During Tests

Capture device logs while tests run:

```powershell
# In one terminal, start logcat
adb logcat -c  # Clear old logs
adb logcat > test-logs.txt

# In another terminal, run tests
.\scripts\test-device.ps1
```

### Testing with Different Build Variants

```powershell
# Test release build
.\gradlew connectedReleaseAndroidTest

# Test specific flavor (if configured)
.\gradlew connectedMyFlavorDebugAndroidTest
```

### Performance Testing

Monitor device performance during tests:

```powershell
# CPU usage
adb shell top -m 10 -n 1

# Memory usage
adb shell dumpsys meminfo com.example.comicbooktts.debug

# Battery stats
adb shell dumpsys battery
```

### Continuous Testing Workflow

For development, use this workflow:

1. Make code changes
2. Run quick test without rebuild:
   ```powershell
   .\gradlew installDebug installDebugAndroidTest
   .\scripts\test-device.ps1 -SkipBuild
   ```

### CI/CD Integration

For automated testing with self-hosted GitHub Actions runner:

```yaml
- name: Run device tests
  run: |
    .\scripts\test-device.ps1 -Verbose

- name: Upload test results
  uses: actions/upload-artifact@v3
  if: always()
  with:
    name: device-test-results
    path: app/build/reports/androidTests/connected/
```

## Test Suite Overview

The ComicTTS instrumentation test suite includes 25+ tests covering:

### UI Tests

- Page navigation (swipe left/right)
- Zoom gestures and controls
- Rotation handling
- Button interactions

### OCR Tests

- Text extraction from comic pages
- Image preprocessing
- Text caching

### TTS Tests

- Text-to-speech initialization
- Playback controls
- Auto-play functionality

### Performance Tests

- Memory pressure handling
- Rapid page switching
- Concurrent operations

### Edge Cases

- Empty pages
- Invalid files
- Low memory scenarios

## Best Practices

1. **Keep Device Charged**: Connect charger during test runs
2. **Close Other Apps**: Free up memory for tests
3. **Disable Animations**: Makes tests faster and more reliable
4. **Keep Screen On**: Enable "Stay awake" in Developer Options
5. **Regular Setup Checks**: Run `setup-device-testing.ps1` after Android SDK updates
6. **Test on Multiple Devices**: Different manufacturers may have quirks
7. **Clean State**: Uninstall app between major test runs for clean state

## Getting Help

If you encounter issues not covered here:

1. Check [README.md](README.md) for general setup
2. Review test output and logs carefully
3. Run setup script to diagnose environment issues
4. Check device logs: `adb logcat`
5. Verify Android SDK and tools are up to date

## Quick Reference

### Common Commands

```powershell
# Setup and verification
.\scripts\setup-device-testing.ps1

# Run tests
.\scripts\test-device.ps1
.\gradlew deviceTest

# Device management
adb devices -l                    # List devices
adb shell getprop ro.build.version.sdk  # Get API level
adb logcat                        # View logs
adb install path\to\app.apk      # Install APK
adb uninstall package.name       # Uninstall app

# Troubleshooting
adb kill-server && adb start-server  # Restart ADB
adb reconnect                    # Reconnect device
.\gradlew clean                  # Clean project
```

### Script Parameters

```powershell
# test-device.ps1
-DeviceSerial <serial>   # Target specific device
-SkipBuild              # Don't rebuild APKs
-Verbose                # Show detailed output
```

---

**Happy Testing!** 🧪
