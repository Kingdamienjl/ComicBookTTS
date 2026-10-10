# APK Build Monitor and Notification System
# This script monitors for new APK builds and sends notifications
# Author: SOLO Coding
# Created: October 2025

# Configuration
$CONFIG = @{
    # Directories to monitor for APK files
    MonitorDirectories = @(
        "d:\Projects\ComicTTS\ComicBookTTS\app\build\outputs\apk\debug",
        "d:\Projects\ComicTTS\ComicBookTTS\app\build\outputs\apk\release"
    )
    
    # APK version log file
    VersionLogFile = "d:\Projects\ComicTTS\ComicBookTTS\apk_versions.json"
    
    # Dashboard HTML file
    DashboardFile = "d:\Projects\ComicTTS\ComicBookTTS\apk_dashboard.html"
    
    # Notification settings
    NotificationTitle = "ComicBookTTS APK Build"
    EnableDesktopNotifications = $true
    EnableEmailNotifications = $false
    
    # Email settings (configure if email notifications enabled)
    EmailFrom = "build-system@example.com"
    EmailTo = "developer@example.com"
    SmtpServer = "smtp.example.com"
    
    # Polling interval in seconds
    PollingInterval = 30
}

# Create directories if they don't exist
$scriptsDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not (Test-Path $scriptsDir)) {
    New-Item -ItemType Directory -Path $scriptsDir -Force | Out-Null
}

# Initialize version log if it doesn't exist
if (-not (Test-Path $CONFIG.VersionLogFile)) {
    @{
        "LastUpdated" = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
        "ApkVersions" = @()
    } | ConvertTo-Json -Depth 10 | Out-File -FilePath $CONFIG.VersionLogFile -Encoding utf8
}

# Function to get APK details
function Get-ApkDetails {
    param (
        [Parameter(Mandatory = $true)]
        [string]$ApkPath
    )
    
    $fileInfo = Get-Item $ApkPath
    
    return @{
        "Path" = $ApkPath
        "FileName" = $fileInfo.Name
        "LastModified" = $fileInfo.LastWriteTime
        "SizeMB" = [math]::Round($fileInfo.Length / 1MB, 2)
        "BuildType" = $(if ($ApkPath -match "debug") { "Debug" } else { "Release" })
        "VersionInfo" = $(if ($fileInfo.Name -match "v(\d+\.\d+\.\d+)") { $matches[1] } else { "Unknown" })
        "Hash" = (Get-FileHash -Path $ApkPath -Algorithm SHA256).Hash
    }
}

# Function to update version log
function Update-VersionLog {
    param (
        [Parameter(Mandatory = $true)]
        [hashtable]$ApkInfo
    )
    
    $versionLog = Get-Content $CONFIG.VersionLogFile -Raw | ConvertFrom-Json
    
    # Check if this APK already exists in the log
    $existingApk = $versionLog.ApkVersions | Where-Object { $_.Path -eq $ApkInfo.Path }
    
    if ($existingApk) {
        # Update existing entry if hash changed
        if ($existingApk.Hash -ne $ApkInfo.Hash) {
            $existingApk.LastModified = $ApkInfo.LastModified
            $existingApk.SizeMB = $ApkInfo.SizeMB
            $existingApk.Hash = $ApkInfo.Hash
            $existingApk.LastUpdated = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
            return $true
        }
        return $false
    }
    else {
        # Add new APK to the log
        $newApkInfo = $ApkInfo.Clone()
        $newApkInfo.FirstDetected = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
        $newApkInfo.LastUpdated = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
        
        $versionLog.ApkVersions += $newApkInfo
        $versionLog.LastUpdated = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
        
        $versionLog | ConvertTo-Json -Depth 10 | Out-File -FilePath $CONFIG.VersionLogFile -Encoding utf8
        return $true
    }
}

# Function to send desktop notification
function Send-DesktopNotification {
    param (
        [Parameter(Mandatory = $true)]
        [hashtable]$ApkInfo
    )
    
    if ($CONFIG.EnableDesktopNotifications) {
        $title = $CONFIG.NotificationTitle
        $message = "New APK build detected!`nFile: $($ApkInfo.FileName)`nLocation: $($ApkInfo.Path)`nSize: $($ApkInfo.SizeMB) MB`nBuild Type: $($ApkInfo.BuildType)"
        
        # Using BurntToast module if available, otherwise fallback to Windows notification
        if (Get-Module -ListAvailable -Name BurntToast) {
            Import-Module BurntToast
            New-BurntToastNotification -Text $title, $message
        }
        else {
            # Fallback to PowerShell notification
            Add-Type -AssemblyName System.Windows.Forms
            $global:balmsg = New-Object System.Windows.Forms.NotifyIcon
            $path = (Get-Process -id $pid).Path
            $balmsg.Icon = [System.Drawing.Icon]::ExtractAssociatedIcon($path)
            $balmsg.BalloonTipIcon = [System.Windows.Forms.ToolTipIcon]::Info
            $balmsg.BalloonTipTitle = $title
            $balmsg.BalloonTipText = $message
            $balmsg.Visible = $true
            $balmsg.ShowBalloonTip(10000)
        }
    }
}

# Function to send email notification
function Send-EmailNotification {
    param (
        [Parameter(Mandatory = $true)]
        [hashtable]$ApkInfo
    )
    
    if ($CONFIG.EnableEmailNotifications) {
        $subject = "$($CONFIG.NotificationTitle) - New APK Available"
        $body = @"
A new APK build has been detected:

File Name: $($ApkInfo.FileName)
Location: $($ApkInfo.Path)
Build Type: $($ApkInfo.BuildType)
Size: $($ApkInfo.SizeMB) MB
Last Modified: $($ApkInfo.LastModified)
Version: $($ApkInfo.VersionInfo)

This is an automated notification from the APK Build Monitor.
"@
        
        try {
            Send-MailMessage -From $CONFIG.EmailFrom -To $CONFIG.EmailTo -Subject $subject -Body $body -SmtpServer $CONFIG.SmtpServer
        }
        catch {
            Write-Warning "Failed to send email notification: $_"
        }
    }
}

# Function to update the dashboard
function Update-Dashboard {
    $versionLog = Get-Content $CONFIG.VersionLogFile -Raw | ConvertFrom-Json
    
    $htmlContent = @"
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>ComicBookTTS APK Dashboard</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            line-height: 1.6;
            margin: 0;
            padding: 20px;
            color: #333;
            background-color: #f5f5f5;
        }
        .container {
            max-width: 1200px;
            margin: 0 auto;
            background-color: #fff;
            padding: 20px;
            border-radius: 5px;
            box-shadow: 0 2px 5px rgba(0,0,0,0.1);
        }
        h1, h2 {
            color: #2c3e50;
        }
        .dashboard-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 20px;
            padding-bottom: 10px;
            border-bottom: 1px solid #eee;
        }
        .last-updated {
            font-size: 0.9em;
            color: #7f8c8d;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            margin-bottom: 20px;
        }
        th, td {
            padding: 12px 15px;
            text-align: left;
            border-bottom: 1px solid #ddd;
        }
        th {
            background-color: #f8f9fa;
            font-weight: bold;
        }
        tr:hover {
            background-color: #f1f1f1;
        }
        .build-type {
            display: inline-block;
            padding: 3px 8px;
            border-radius: 3px;
            font-size: 0.8em;
            font-weight: bold;
        }
        .build-type.debug {
            background-color: #e74c3c;
            color: white;
        }
        .build-type.release {
            background-color: #2ecc71;
            color: white;
        }
        .summary {
            display: flex;
            justify-content: space-between;
            margin-bottom: 20px;
        }
        .summary-box {
            flex: 1;
            background-color: #f8f9fa;
            padding: 15px;
            border-radius: 5px;
            margin-right: 10px;
            box-shadow: 0 1px 3px rgba(0,0,0,0.1);
        }
        .summary-box:last-child {
            margin-right: 0;
        }
        .summary-box h3 {
            margin-top: 0;
            color: #34495e;
        }
        .summary-box p {
            font-size: 1.5em;
            font-weight: bold;
            margin: 5px 0;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="dashboard-header">
            <h1>ComicBookTTS APK Dashboard</h1>
            <div class="last-updated">Last updated: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")</div>
        </div>
        
        <div class="summary">
            <div class="summary-box">
                <h3>Total APKs</h3>
                <p>$($versionLog.ApkVersions.Count)</p>
            </div>
            <div class="summary-box">
                <h3>Release Builds</h3>
                <p>$($versionLog.ApkVersions | Where-Object { $_.BuildType -eq "Release" } | Measure-Object).Count</p>
            </div>
            <div class="summary-box">
                <h3>Debug Builds</h3>
                <p>$($versionLog.ApkVersions | Where-Object { $_.BuildType -eq "Debug" } | Measure-Object).Count</p>
            </div>
            <div class="summary-box">
                <h3>Latest Build</h3>
                <p>$($versionLog.ApkVersions | Sort-Object LastModified -Descending | Select-Object -First 1 -ExpandProperty LastModified)</p>
            </div>
        </div>
        
        <h2>APK Version History</h2>
        <table>
            <thead>
                <tr>
                    <th>File Name</th>
                    <th>Build Type</th>
                    <th>Version</th>
                    <th>Size (MB)</th>
                    <th>Last Modified</th>
                    <th>Location</th>
                </tr>
            </thead>
            <tbody>
"@

    # Add each APK to the table
    foreach ($apk in ($versionLog.ApkVersions | Sort-Object LastModified -Descending)) {
        $buildTypeClass = if ($apk.BuildType -eq "Debug") { "debug" } else { "release" }
        
        $htmlContent += @"
                <tr>
                    <td>$($apk.FileName)</td>
                    <td><span class="build-type $buildTypeClass">$($apk.BuildType)</span></td>
                    <td>$($apk.VersionInfo)</td>
                    <td>$($apk.SizeMB)</td>
                    <td>$($apk.LastModified)</td>
                    <td>$($apk.Path)</td>
                </tr>
"@
    }

    $htmlContent += @"
            </tbody>
        </table>
    </div>
    <script>
        // Auto-refresh the page every 5 minutes
        setTimeout(function() {
            location.reload();
        }, 300000);
    </script>
</body>
</html>
"@

    $htmlContent | Out-File -FilePath $CONFIG.DashboardFile -Encoding utf8
}

# Main monitoring function
function Start-ApkMonitoring {
    Write-Host "Starting APK Build Monitor..."
    Write-Host "Monitoring directories:"
    $CONFIG.MonitorDirectories | ForEach-Object { Write-Host "  - $_" }
    Write-Host "Dashboard will be available at: $($CONFIG.DashboardFile)"
    Write-Host "Press Ctrl+C to stop monitoring."
    
    try {
        while ($true) {
            $newApkDetected = $false
            
            foreach ($directory in $CONFIG.MonitorDirectories) {
                if (Test-Path $directory) {
                    $apkFiles = Get-ChildItem -Path $directory -Filter "*.apk" -File
                    
                    foreach ($apkFile in $apkFiles) {
                        $apkInfo = Get-ApkDetails -ApkPath $apkFile.FullName
                        $isNewOrUpdated = Update-VersionLog -ApkInfo $apkInfo
                        
                        if ($isNewOrUpdated) {
                            $newApkDetected = $true
                            Write-Host "New or updated APK detected: $($apkInfo.Path)"
                            Send-DesktopNotification -ApkInfo $apkInfo
                            Send-EmailNotification -ApkInfo $apkInfo
                        }
                    }
                }
                else {
                    Write-Warning "Directory not found: $directory"
                }
            }
            
            if ($newApkDetected) {
                Update-Dashboard
            }
            
            Start-Sleep -Seconds $CONFIG.PollingInterval
        }
    }
    catch {
        Write-Error "Error in APK monitoring: $_"
    }
    finally {
        Write-Host "APK monitoring stopped."
    }
}

# Function to list current APKs
function Get-CurrentApks {
    $results = @()
    
    foreach ($directory in $CONFIG.MonitorDirectories) {
        if (Test-Path $directory) {
            $apkFiles = Get-ChildItem -Path $directory -Filter "*.apk" -File
            
            foreach ($apkFile in $apkFiles) {
                $apkInfo = Get-ApkDetails -ApkPath $apkFile.FullName
                $results += $apkInfo
            }
        }
    }
    
    # Also check root project directory
    $rootDir = "d:\Projects\ComicTTS\ComicBookTTS"
    if (Test-Path $rootDir) {
        $apkFiles = Get-ChildItem -Path $rootDir -Filter "*.apk" -File
        
        foreach ($apkFile in $apkFiles) {
            $apkInfo = Get-ApkDetails -ApkPath $apkFile.FullName
            $results += $apkInfo
        }
    }
    
    return $results | Sort-Object LastModified -Descending
}

# Initial dashboard creation
$initialApks = Get-CurrentApks
foreach ($apk in $initialApks) {
    Update-VersionLog -ApkInfo $apk
}
Update-Dashboard

# Display current APK information
Write-Host "Current APK Files:" -ForegroundColor Green
$initialApks | Format-Table FileName, BuildType, SizeMB, LastModified, Path -AutoSize

# Instructions for usage
Write-Host @"

APK Build Monitor and Notification System
----------------------------------------
This script has been set up to monitor for new APK builds and provide notifications.

Current APK locations:
$($initialApks | ForEach-Object { "  - $($_.Path)" })

To start monitoring for new APK builds:
  .\scripts\apk_monitor.ps1

APK Dashboard: $($CONFIG.DashboardFile)
APK Version Log: $($CONFIG.VersionLogFile)

"@ -ForegroundColor Cyan

# If script is being run directly (not dot-sourced), start monitoring
if ($MyInvocation.InvocationName -ne ".") {
    # Uncomment to start monitoring automatically
    # Start-ApkMonitoring
}