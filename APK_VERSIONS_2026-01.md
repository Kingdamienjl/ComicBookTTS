# ComicBookTTS APK Version Documentation

This document provides comprehensive information about all APK versions of the ComicBookTTS application, including their locations, build types, and other relevant details.

# ComicBookTTS APK Versions

## Latest APK Versions

### Debug APK v1.1.0 (Latest - With PDF & Night Mode)
- **Location**: `app/build/outputs/apk/debug/app-debug.apk`
- **Version**: 1.1.0 (versionCode: 2)
- **Build Date**: 2025-01-14
- **Status**: ✅ Ready for Testing
- **Size**: ~8.5 MB
- **Includes**: 
  - Comic opening crash fixes
  - **NEW**: PDF comic support with high-quality rendering
  - **NEW**: Night Mode with comprehensive dark theme
  - TTS improvements and stability enhancements
  - UI/UX updates with better error handling
  - Memory management optimizations
  - Enhanced resource cleanup and error recovery

### Production Release v1.1.0 (Latest)
- **Location**: `app/build/outputs/apk/release/app-release.apk`
- **Version**: 1.1.0 (versionCode: 2)
- **Build Date**: 2025-01-14
- **Status**: ✅ Production Ready
- **Size**: ~6.8 MB
- **Includes**: 
  - Comic opening crash fixes (CBZ, CBR, PDF support)
  - TTS improvements and stability enhancements
  - UI/UX updates with better error handling
  - Memory management optimizations
  - Enhanced resource cleanup and error recovery

## Current APK Locations

### Debug APK (Latest Build - Crash Fixes)
- **Location**: `D:\Projects\ComicTTS\ComicBookTTS\app\build\outputs\apk\debug\app-debug.apk`
- **Size**: 46,739,439 bytes (44.6 MB)
- **Last Modified**: October 12, 2025, 2:18:57 AM
- **Build Type**: Debug
- **Status**: ✅ Ready for Testing
- **Recent Fixes**: 
  - Fixed critical app crash when opening comics
  - Enhanced null pointer exception handling in ComicArchiveExtractor
  - Improved memory management for ZIP/RAR extraction
  - Added comprehensive error handling in ComicPageAdapter
  - Fixed cursor validation in filename extraction
  - Enhanced resource cleanup and memory leak prevention

### Latest Production APK
- **Location**: `d:\Projects\ComicTTS\ComicBookTTS\app\build\outputs\apk\release\app-release.apk`
- **Last Modified**: September 6, 2025 at 1:55:19 PM
- **Size**: 40.34 MB
- **Type**: Release Build
- **Status**: Production-Ready

### Released Versions
- **ComicBookTTS-v1.0.0-release.apk**
  - **Location**: `d:\Projects\ComicTTS\ComicBookTTS\ComicBookTTS-v1.0.0-release.apk`
  - **Last Modified**: September 5, 2025 at 1:35:45 AM
  - **Size**: 40.33 MB
  - **Type**: Release Build (Tagged Version)
  - **Status**: Official Release

- **ComicBookTTS.apk**
  - **Location**: `d:\Projects\ComicTTS\ComicBookTTS\ComicBookTTS.apk`
  - **Last Modified**: September 5, 2025 at 12:50:45 AM
  - **Size**: 44.97 MB
  - **Type**: Release Build
  - **Status**: Stable Release

## Recent Updates (October 14, 2025)

### Production Release v1.1.0 - DEPLOYED ✅
- **Location**: `d:\Projects\ComicTTS\ComicBookTTS\app\build\outputs\apk\release\app-release.apk`
- **Version**: v1.1.0 (Build 2)
- **Build Date**: October 14, 2025
- **Status**: ✅ Production Ready
- **Size**: Optimized for release
- **Includes All Fixes**: 
  - ✅ Comic opening crash fixes
  - ✅ TTS system improvements (natural speech, functional controls)
  - ✅ UI/UX enhancements (slideshow sync, 8x zoom, media controls)
  - ✅ Memory management optimizations
  - ✅ Enhanced error handling and resource cleanup

## Previous Updates (October 11, 2025)

### APK Notification System Activated
- ✅ Setup script executed successfully
- ✅ Desktop shortcuts created for APK Monitor and Dashboard
- ✅ Scheduled task configured for automatic monitoring
- ✅ Test notification sent successfully
- ✅ APK Dashboard launched and accessible

### UI/UX Fixes Implemented
1. **Slideshow Timing Synchronization**:
   - Reduced buffer times in `calculateDynamicWaitTime()`
   - Improved TTS audio synchronization
   - Reduced default slideshow speed to 2500ms

2. **Zoom Functionality Enhancement**:
   - Increased maximum zoom to 8.0x
   - Added minimum zoom of 0.5x
   - Changed scale type to `matrix` for better image handling
   - Improved text readability at high zoom levels

3. **Media Control Button Layout**:
   - Reorganized buttons into two rows
   - Increased spacing between controls
   - Standardized button sizes
   - Prevented overlapping issues

## APK Monitoring System

An automated APK monitoring system has been implemented to track new builds and provide notifications:

### Features
- **Real-time Monitoring**: Automatically detects new APK builds
- **Desktop Notifications**: Receive alerts when new builds are available
- **Version Tracking**: Maintains a comprehensive log of all APK versions
- **Build Dashboard**: Visual representation of APK build history

### How to Use

1. **Start APK Monitoring**:
   ```powershell
   cd d:\Projects\ComicTTS\ComicBookTTS
   .\scripts\apk_monitor.ps1
   ```

2. **View APK Dashboard**:
   Open `d:\Projects\ComicTTS\ComicBookTTS\apk_dashboard.html` in your browser

3. **Check Version Log**:
   The version log is maintained at `d:\Projects\ComicTTS\ComicBookTTS\apk_versions.json`

## Build Process

APKs are generated through the following methods:

1. **Local Development Build**:
   ```bash
   cd d:\Projects\ComicTTS\ComicBookTTS
   ./gradlew assembleDebug
   ```
   Output: `app/build/outputs/apk/debug/app-debug.apk`

2. **Local Release Build**:
   ```bash
   cd d:\Projects\ComicTTS\ComicBookTTS
   ./gradlew assembleRelease
   ```
   Output: `app/build/outputs/apk/release/app-release.apk`

3. **CI/CD Pipeline**:
   Automated builds are triggered through GitHub Actions workflows:
   - Debug builds: `.github/workflows/ci-cd.yml`
   - Release builds: `.github/workflows/release.yml`

## APK Signing Information

Release APKs are signed using the keystore located at:
- **Keystore Path**: `d:\Projects\ComicTTS\ComicBookTTS\app\keystore\release.keystore`
- **Configuration**: Defined in `app/build.gradle` under `signingConfigs`

## Testing Guidelines

When testing APKs:

1. **Install on Device**:
   ```bash
   adb install "d:\Projects\ComicTTS\ComicBookTTS\app\build\outputs\apk\debug\app-debug.apk"
   ```

2. **Verify Version**:
   Check the app's About screen to confirm the correct version is installed

3. **Report Issues**:
   Document any issues in the project's issue tracker with the APK version and build date

## Notification System Configuration

The APK notification system can be configured by editing:
```
d:\Projects\ComicTTS\ComicBookTTS\scripts\apk_monitor.ps1
```

Configuration options include:
- Directories to monitor
- Notification methods (desktop, email)
- Polling interval
- Dashboard customization

## APK Monitoring System Features

### Automated Monitoring
- **Real-time Detection**: Monitors for new APK builds every 30 seconds
- **Desktop Notifications**: Uses BurntToast for enhanced Windows notifications
- **Build Dashboard**: Web-based interface for tracking APK versions and builds
- **Check Interval**: 30-second monitoring cycle for immediate detection

### Notification Capabilities
- **BurntToast Module**: ✅ Installed and configured for rich notifications
- **GitHub CLI Integration**: ✅ Available for repository operations
- **APK Dashboard**: ✅ Accessible at `apk_dashboard.html`
- **Configuration File**: ✅ Located at `apk_config.json`

### Usage Instructions
1. **Monitor APKs**: Run `.\apk_monitor.ps1` to start real-time monitoring
2. **View Dashboard**: Open `apk_dashboard.html` in your browser for build tracking
3. **Manual Check**: Use `.\ci_integration.ps1 -Action status` for current status
4. **Setup System**: Run `.\setup_notifications.ps1` to configure the monitoring system

### Manual Commands
- `.\apk_monitor.ps1` - Start APK monitoring
- `.\ci_integration.ps1 -Action status` - Check system status
- `.\ci_integration.ps1 -Action build` - Trigger new build
- Open `apk_dashboard.html` - View build dashboard

## System Status
- **APK Notification System**: ✅ Fully Operational
- **Last System Check**: October 12, 2025, 2:18:57 AM
- **Monitoring Status**: Active and ready for use
- **Critical Bug Status**: ✅ RESOLVED - App crash when opening comics has been fixed