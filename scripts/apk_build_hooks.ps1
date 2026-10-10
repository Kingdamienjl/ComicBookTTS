# APK Build Hooks and File Watchers
# This script sets up hooks to detect new APK builds and trigger notifications
# Author: SOLO Coding
# Created: October 2025

# Import the APK monitor script
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$monitorScript = Join-Path $scriptPath "apk_monitor.ps1"

# Source the monitor script to access its functions
. $monitorScript

# Configuration
$CONFIG = @{
    # Directories to watch for changes
    WatchDirectories = @(
        "d:\Projects\ComicTTS\ComicBookTTS\app\build\outputs\apk\debug",
        "d:\Projects\ComicTTS\ComicBookTTS\app\build\outputs\apk\release"
    )
    
    # File patterns to watch
    FileFilter = "*.apk"
    
    # Log file for build events
    LogFile = "d:\Projects\ComicTTS\ComicBookTTS\apk_build_events.log"
    
    # Whether to run in background
    RunAsJob = $true
}

# Ensure log directory exists
$logDir = Split-Path -Parent $CONFIG.LogFile
if (-not (Test-Path $logDir)) {
    New-Item -ItemType Directory -Path $logDir -Force | Out-Null
}

# Function to log events
function Write-BuildLog {
    param (
        [Parameter(Mandatory = $true)]
        [string]$Message
    )
    
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    "$timestamp - $Message" | Out-File -FilePath $CONFIG.LogFile -Append
    Write-Host "$timestamp - $Message"
}

# Function to handle file system events
function Handle-FileSystemEvent {
    param (
        [Parameter(Mandatory = $true)]
        [System.IO.FileSystemEventArgs]$Event
    )
    
    # Only process APK files
    if ($Event.ChangeType -eq 'Created' -or $Event.ChangeType -eq 'Changed') {
        if ($Event.Name -match '\.apk$') {
            Write-BuildLog "Detected $($Event.ChangeType) event for APK: $($Event.FullPath)"
            
            # Get APK details
            $apkInfo = Get-ApkDetails -ApkPath $Event.FullPath
            
            # Update version log
            $isNewOrUpdated = Update-VersionLog -ApkInfo $apkInfo
            
            if ($isNewOrUpdated) {
                Write-BuildLog "New or updated APK detected: $($apkInfo.Path)"
                
                # Send notifications
                Send-DesktopNotification -ApkInfo $apkInfo
                Send-EmailNotification -ApkInfo $apkInfo
                
                # Update dashboard
                Update-Dashboard
            }
        }
    }
}

# Function to set up file system watchers
function Register-FileSystemWatchers {
    $watchers = @()
    
    foreach ($directory in $CONFIG.WatchDirectories) {
        if (Test-Path $directory) {
            Write-BuildLog "Setting up watcher for directory: $directory"
            
            $watcher = New-Object System.IO.FileSystemWatcher
            $watcher.Path = $directory
            $watcher.Filter = $CONFIG.FileFilter
            $watcher.IncludeSubdirectories = $false
            $watcher.EnableRaisingEvents = $true
            
            # Register event handlers
            $handlers = @{}
            $handlers.Created = Register-ObjectEvent -InputObject $watcher -EventName Created -Action {
                Handle-FileSystemEvent -Event $Event.SourceEventArgs
            }
            $handlers.Changed = Register-ObjectEvent -InputObject $watcher -EventName Changed -Action {
                Handle-FileSystemEvent -Event $Event.SourceEventArgs
            }
            
            $watchers += @{
                Watcher = $watcher
                Handlers = $handlers
            }
        }
        else {
            Write-BuildLog "Warning: Directory not found: $directory"
        }
    }
    
    return $watchers
}

# Function to set up Gradle build hooks
function Register-GradleBuildHooks {
    $gradlePropertiesPath = "d:\Projects\ComicTTS\ComicBookTTS\gradle.properties"
    
    if (Test-Path $gradlePropertiesPath) {
        Write-BuildLog "Setting up Gradle build hooks"
        
        # Create a backup of the original file
        Copy-Item -Path $gradlePropertiesPath -Destination "$gradlePropertiesPath.bak" -Force
        
        # Read the current content
        $content = Get-Content -Path $gradlePropertiesPath -Raw
        
        # Add our custom hook if it doesn't exist
        if (-not ($content -match "org.gradle.addbuildhook")) {
            $hookScript = @"

# APK Build Notification Hook
org.gradle.addbuildhook=true
org.gradle.buildfinished=powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$scriptPath\apk_build_notification.ps1"

"@
            
            Add-Content -Path $gradlePropertiesPath -Value $hookScript
            Write-BuildLog "Added Gradle build hook to gradle.properties"
        }
        else {
            Write-BuildLog "Gradle build hook already exists in gradle.properties"
        }
    }
    else {
        Write-BuildLog "Warning: gradle.properties not found at $gradlePropertiesPath"
    }
}

# Create the build notification script
function Create-BuildNotificationScript {
    $notificationScriptPath = Join-Path $scriptPath "apk_build_notification.ps1"
    
    $scriptContent = @"
# APK Build Notification Script
# This script is called by Gradle after a build completes
# Author: SOLO Coding
# Created: October 2025

# Import the APK monitor script
`$scriptPath = Split-Path -Parent `$MyInvocation.MyCommand.Path
`$monitorScript = Join-Path `$scriptPath "apk_monitor.ps1"

# Source the monitor script to access its functions
. `$monitorScript

# Log the build completion
`$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
"`$timestamp - Gradle build completed" | Out-File -FilePath "d:\Projects\ComicTTS\ComicBookTTS\apk_build_events.log" -Append

# Check for new APKs
`$apks = Get-CurrentApks
foreach (`$apk in `$apks) {
    # Update version log
    `$isNewOrUpdated = Update-VersionLog -ApkInfo `$apk
    
    if (`$isNewOrUpdated) {
        # Send notifications
        Send-DesktopNotification -ApkInfo `$apk
        Send-EmailNotification -ApkInfo `$apk
    }
}

# Update dashboard
Update-Dashboard
"@
    
    $scriptContent | Out-File -FilePath $notificationScriptPath -Encoding utf8
    Write-BuildLog "Created build notification script at $notificationScriptPath"
}

# Function to set up a scheduled task for periodic checking
function Register-ScheduledTask {
    $taskName = "ComicBookTTS_APK_Monitor"
    $taskExists = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    
    if ($taskExists) {
        Write-BuildLog "Scheduled task $taskName already exists"
        return
    }
    
    Write-BuildLog "Setting up scheduled task for periodic APK checking"
    
    $action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$monitorScript`""
    $trigger = New-ScheduledTaskTrigger -Once -At (Get-Date) -RepetitionInterval (New-TimeSpan -Minutes 15)
    $settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable
    
    try {
        Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings -Description "Monitors for new ComicBookTTS APK builds"
        Write-BuildLog "Scheduled task $taskName created successfully"
    }
    catch {
        Write-BuildLog "Error creating scheduled task: $_"
    }
}

# Main function to set up all hooks
function Setup-ApkBuildHooks {
    Write-BuildLog "Setting up APK build hooks and file watchers"
    
    # Create the build notification script
    Create-BuildNotificationScript
    
    # Set up file system watchers
    $watchers = Register-FileSystemWatchers
    
    # Set up Gradle build hooks
    Register-GradleBuildHooks
    
    # Set up scheduled task
    Register-ScheduledTask
    
    Write-BuildLog "APK build hooks and file watchers setup complete"
    
    # If running as a job, keep the script running to maintain watchers
    if ($CONFIG.RunAsJob) {
        Write-BuildLog "Running in background mode. Press Ctrl+C to stop."
        try {
            while ($true) {
                Start-Sleep -Seconds 60
            }
        }
        finally {
            # Clean up watchers when script is terminated
            foreach ($watcher in $watchers) {
                foreach ($handler in $watcher.Handlers.Values) {
                    Unregister-Event -SubscriptionId $handler.Id -Force
                }
            }
            Write-BuildLog "APK build hooks and file watchers stopped"
        }
    }
}

# Instructions for usage
Write-Host @"

APK Build Hooks and File Watchers
---------------------------------
This script sets up hooks and file watchers to detect new APK builds and trigger notifications.

Features:
- File system watchers for APK directories
- Gradle build hooks for build completion events
- Scheduled task for periodic checking
- Integration with APK monitoring system

To set up all hooks and watchers:
  .\scripts\apk_build_hooks.ps1

"@ -ForegroundColor Cyan

# If script is being run directly (not dot-sourced), set up hooks
if ($MyInvocation.InvocationName -ne ".") {
    # Uncomment to set up hooks automatically
    # Setup-ApkBuildHooks
}