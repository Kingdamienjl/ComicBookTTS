# CI/CD Integration Script for APK Notification System
# This script integrates with GitHub Actions to provide local notifications

param(
    [string]$Action = "monitor",
    [string]$ConfigFile = "scripts\apk_config.json"
)

# Configuration
$Config = @{
    GitHubRepo = "ComicBookTTS"
    WorkflowName = "CI/CD Pipeline"
    LocalAPKPaths = @(
        "app\build\outputs\apk\debug\app-debug.apk",
        "app\build\outputs\apk\release\app-release.apk"
    )
    NotificationSettings = @{
        EnableDesktop = $true
        EnableEmail = $false
        CheckInterval = 300  # 5 minutes
    }
}

# Load configuration if exists
if (Test-Path $ConfigFile) {
    try {
        $LoadedConfig = Get-Content $ConfigFile | ConvertFrom-Json
        $Config = $LoadedConfig
        Write-Host "Configuration loaded from $ConfigFile" -ForegroundColor Green
    }
    catch {
        Write-Warning "Failed to load configuration from $ConfigFile. Using defaults."
    }
}

function Write-Log {
    param([string]$Message, [string]$Level = "INFO")
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $logMessage = "[$timestamp] [$Level] $Message"
    Write-Host $logMessage
    
    # Append to log file
    $logFile = "logs\ci_integration.log"
    if (!(Test-Path "logs")) { New-Item -ItemType Directory -Path "logs" -Force | Out-Null }
    Add-Content -Path $logFile -Value $logMessage
}

function Get-GitHubWorkflowRuns {
    param([string]$RepoOwner, [string]$RepoName, [int]$Count = 5)
    
    try {
        # Check if GitHub CLI is available
        $ghVersion = gh --version 2>$null
        if ($LASTEXITCODE -eq 0) {
            Write-Log "Using GitHub CLI to fetch workflow runs"
            $runs = gh run list --repo "$RepoOwner/$RepoName" --workflow="CI/CD Pipeline" --limit=$Count --json=status,conclusion,createdAt,headSha,url
            return $runs | ConvertFrom-Json
        }
        else {
            Write-Log "GitHub CLI not available. Skipping remote workflow check." -Level "WARN"
            return @()
        }
    }
    catch {
        Write-Log "Failed to fetch GitHub workflow runs: $($_.Exception.Message)" -Level "ERROR"
        return @()
    }
}

function Check-LocalAPKChanges {
    $changes = @()
    
    foreach ($apkPath in $Config.LocalAPKPaths) {
        if (Test-Path $apkPath) {
            $apkInfo = Get-Item $apkPath
            $lastModified = $apkInfo.LastWriteTime
            
            # Check if this is a recent change (within last 10 minutes)
            $timeDiff = (Get-Date) - $lastModified
            if ($timeDiff.TotalMinutes -le 10) {
                $changes += @{
                    Path = $apkPath
                    LastModified = $lastModified
                    Size = [math]::Round($apkInfo.Length / 1MB, 2)
                    Type = if ($apkPath -like "*debug*") { "Debug" } else { "Release" }
                }
            }
        }
    }
    
    return $changes
}

function Send-APKNotification {
    param(
        [array]$LocalChanges,
        [array]$WorkflowRuns
    )
    
    if ($LocalChanges.Count -eq 0 -and $WorkflowRuns.Count -eq 0) {
        return
    }
    
    $notificationTitle = "ComicBookTTS APK Update"
    $notificationBody = ""
    
    # Local changes
    if ($LocalChanges.Count -gt 0) {
        $notificationBody += "Local APK Changes:`n"
        foreach ($change in $LocalChanges) {
            $notificationBody += "• $($change.Type) APK updated at $($change.LastModified) ($($change.Size) MB)`n"
            $notificationBody += "  Path: $($change.Path)`n"
        }
        $notificationBody += "`n"
    }
    
    # Workflow runs
    if ($WorkflowRuns.Count -gt 0) {
        $latestRun = $WorkflowRuns[0]
        if ($latestRun.status -eq "completed") {
            $notificationBody += "Latest CI/CD Build: $($latestRun.conclusion.ToUpper())`n"
            $notificationBody += "Commit: $($latestRun.headSha.Substring(0,7))`n"
            $notificationBody += "Time: $($latestRun.createdAt)`n"
            $notificationBody += "URL: $($latestRun.url)`n"
        }
    }
    
    # Send desktop notification
    if ($Config.NotificationSettings.EnableDesktop) {
        try {
            # Try using BurntToast if available
            if (Get-Module -ListAvailable -Name BurntToast) {
                Import-Module BurntToast
                New-BurntToastNotification -Text $notificationTitle, $notificationBody
                Write-Log "Desktop notification sent via BurntToast"
            }
            else {
                # Fallback to PowerShell notification
                Add-Type -AssemblyName System.Windows.Forms
                $notification = New-Object System.Windows.Forms.NotifyIcon
                $notification.Icon = [System.Drawing.SystemIcons]::Information
                $notification.BalloonTipTitle = $notificationTitle
                $notification.BalloonTipText = $notificationBody
                $notification.Visible = $true
                $notification.ShowBalloonTip(5000)
                Write-Log "Desktop notification sent via System.Windows.Forms"
                
                # Clean up
                Start-Sleep -Seconds 6
                $notification.Dispose()
            }
        }
        catch {
            Write-Log "Failed to send desktop notification: $($_.Exception.Message)" -Level "ERROR"
        }
    }
}

function Update-APKDashboard {
    param([array]$LocalChanges, [array]$WorkflowRuns)
    
    $dashboardPath = "apk_dashboard.html"
    if (!(Test-Path $dashboardPath)) {
        Write-Log "Dashboard file not found at $dashboardPath" -Level "WARN"
        return
    }
    
    try {
        # Update last updated timestamp in dashboard
        $dashboardContent = Get-Content $dashboardPath -Raw
        $currentTime = Get-Date -Format "MMMM dd, yyyy h:mm:ss tt"
        $dashboardContent = $dashboardContent -replace 'Last updated: [^<]+', "Last updated: $currentTime"
        
        # Update summary statistics if needed
        $allAPKs = Get-ChildItem -Path . -Filter "*.apk" -Recurse
        $totalAPKs = $allAPKs.Count
        $releaseAPKs = ($allAPKs | Where-Object { $_.Name -like "*release*" }).Count
        $debugAPKs = ($allAPKs | Where-Object { $_.Name -like "*debug*" }).Count
        
        $dashboardContent = $dashboardContent -replace '<p>(\d+)</p>', "<p>$totalAPKs</p>"
        
        Set-Content -Path $dashboardPath -Value $dashboardContent
        Write-Log "Dashboard updated successfully"
    }
    catch {
        Write-Log "Failed to update dashboard: $($_.Exception.Message)" -Level "ERROR"
    }
}

function Start-CIIntegrationMonitoring {
    Write-Log "Starting CI/CD Integration Monitoring..."
    Write-Log "Check interval: $($Config.NotificationSettings.CheckInterval) seconds"
    
    while ($true) {
        try {
            # Check for local APK changes
            $localChanges = Check-LocalAPKChanges
            
            # Check GitHub workflow runs (if available)
            $workflowRuns = @()
            if ($Config.GitHubRepo) {
                # Assume current directory is the repo
                $workflowRuns = Get-GitHubWorkflowRuns -RepoOwner "user" -RepoName $Config.GitHubRepo
            }
            
            # Send notifications if there are changes
            if ($localChanges.Count -gt 0 -or $workflowRuns.Count -gt 0) {
                Send-APKNotification -LocalChanges $localChanges -WorkflowRuns $workflowRuns
                Update-APKDashboard -LocalChanges $localChanges -WorkflowRuns $workflowRuns
            }
            
            Write-Log "Monitoring cycle completed. Next check in $($Config.NotificationSettings.CheckInterval) seconds."
        }
        catch {
            Write-Log "Error during monitoring cycle: $($_.Exception.Message)" -Level "ERROR"
        }
        
        Start-Sleep -Seconds $Config.NotificationSettings.CheckInterval
    }
}

function Show-CIIntegrationStatus {
    Write-Host "`n=== ComicBookTTS CI/CD Integration Status ===" -ForegroundColor Cyan
    
    # Check GitHub CLI availability
    $ghAvailable = $false
    try {
        gh --version | Out-Null
        $ghAvailable = $true
        Write-Host "✓ GitHub CLI: Available" -ForegroundColor Green
    }
    catch {
        Write-Host "✗ GitHub CLI: Not available" -ForegroundColor Red
    }
    
    # Check local APK files
    Write-Host "`nLocal APK Files:" -ForegroundColor Yellow
    foreach ($apkPath in $Config.LocalAPKPaths) {
        if (Test-Path $apkPath) {
            $apkInfo = Get-Item $apkPath
            Write-Host "✓ $apkPath" -ForegroundColor Green
            Write-Host "  Last Modified: $($apkInfo.LastWriteTime)" -ForegroundColor Gray
            Write-Host "  Size: $([math]::Round($apkInfo.Length / 1MB, 2)) MB" -ForegroundColor Gray
        }
        else {
            Write-Host "✗ $apkPath (Not found)" -ForegroundColor Red
        }
    }
    
    # Check notification capabilities
    Write-Host "`nNotification Capabilities:" -ForegroundColor Yellow
    if (Get-Module -ListAvailable -Name BurntToast) {
        Write-Host "✓ BurntToast module: Available" -ForegroundColor Green
    }
    else {
        Write-Host "○ BurntToast module: Not installed (using fallback)" -ForegroundColor Yellow
    }
    
    # Check dashboard
    if (Test-Path "apk_dashboard.html") {
        Write-Host "✓ APK Dashboard: Available" -ForegroundColor Green
    }
    else {
        Write-Host "✗ APK Dashboard: Not found" -ForegroundColor Red
    }
    
    Write-Host "`n=== Configuration ===" -ForegroundColor Cyan
    Write-Host "Repository: $($Config.GitHubRepo)"
    Write-Host "Check Interval: $($Config.NotificationSettings.CheckInterval) seconds"
    Write-Host "Desktop Notifications: $($Config.NotificationSettings.EnableDesktop)"
    Write-Host "Email Notifications: $($Config.NotificationSettings.EnableEmail)"
}

# Main execution
switch ($Action.ToLower()) {
    "monitor" {
        Start-CIIntegrationMonitoring
    }
    "status" {
        Show-CIIntegrationStatus
    }
    "check" {
        $localChanges = Check-LocalAPKChanges
        $workflowRuns = Get-GitHubWorkflowRuns -RepoOwner "user" -RepoName $Config.GitHubRepo
        Send-APKNotification -LocalChanges $localChanges -WorkflowRuns $workflowRuns
        Update-APKDashboard -LocalChanges $localChanges -WorkflowRuns $workflowRuns
    }
    default {
        Write-Host "ComicBookTTS CI/CD Integration Script" -ForegroundColor Cyan
        Write-Host "Usage: .\ci_integration.ps1 [-Action <monitor|status|check>]" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "Actions:"
        Write-Host "  monitor  - Start continuous monitoring (default)"
        Write-Host "  status   - Show current integration status"
        Write-Host "  check    - Perform one-time check and notification"
        Write-Host ""
        Write-Host "Examples:"
        Write-Host "  .\ci_integration.ps1                    # Start monitoring"
        Write-Host "  .\ci_integration.ps1 -Action status     # Show status"
        Write-Host "  .\ci_integration.ps1 -Action check      # One-time check"
    }
}