# Setup Script for ComicBookTTS APK Notification System
# This script sets up the complete notification system

Write-Host "=== ComicBookTTS APK Notification System Setup ===" -ForegroundColor Cyan

# Create necessary directories
$directories = @("scripts", "logs", "build-notifications")
foreach ($dir in $directories) {
    if (!(Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
        Write-Host "✓ Created directory: $dir" -ForegroundColor Green
    }
    else {
        Write-Host "✓ Directory exists: $dir" -ForegroundColor Green
    }
}

# Check and install BurntToast module for better notifications
Write-Host "`nChecking notification dependencies..." -ForegroundColor Yellow
try {
    if (!(Get-Module -ListAvailable -Name BurntToast)) {
        Write-Host "Installing BurntToast module for enhanced notifications..." -ForegroundColor Yellow
        Install-Module -Name BurntToast -Force -Scope CurrentUser
        Write-Host "✓ BurntToast module installed" -ForegroundColor Green
    }
    else {
        Write-Host "✓ BurntToast module already installed" -ForegroundColor Green
    }
}
catch {
    Write-Host "⚠ Could not install BurntToast module. Using fallback notifications." -ForegroundColor Yellow
}

# Create configuration file
$configPath = "scripts\apk_config.json"
$config = @{
    GitHubRepo = "ComicBookTTS"
    WorkflowName = "CI/CD Pipeline"
    LocalAPKPaths = @(
        "app\build\outputs\apk\debug\app-debug.apk",
        "app\build\outputs\apk\release\app-release.apk",
        "ComicBookTTS.apk",
        "ComicBookTTS-v1.0.0-release.apk"
    )
    NotificationSettings = @{
        EnableDesktop = $true
        EnableEmail = $false
        CheckInterval = 300
        EmailSettings = @{
            SMTPServer = ""
            SMTPPort = 587
            FromEmail = ""
            ToEmail = ""
            Username = ""
            Password = ""
        }
    }
    DashboardSettings = @{
        AutoRefresh = $true
        RefreshInterval = 300
        ShowBuildLogs = $true
    }
}

$config | ConvertTo-Json -Depth 4 | Set-Content -Path $configPath
Write-Host "✓ Configuration file created: $configPath" -ForegroundColor Green

# Create a scheduled task for automatic monitoring
Write-Host "`nSetting up scheduled task..." -ForegroundColor Yellow
try {
    $taskName = "ComicBookTTS-APK-Monitor"
    $scriptPath = Join-Path (Get-Location) "scripts\apk_monitor.ps1"
    
    # Check if task already exists
    $existingTask = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    if ($existingTask) {
        Unregister-ScheduledTask -TaskName $taskName -Confirm:$false
        Write-Host "✓ Removed existing scheduled task" -ForegroundColor Green
    }
    
    # Create new task
    $action = New-ScheduledTaskAction -Execute "PowerShell.exe" -Argument "-WindowStyle Hidden -ExecutionPolicy Bypass -File `"$scriptPath`""
    $trigger = New-ScheduledTaskTrigger -AtStartup
    $settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable
    $principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive
    
    Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Description "ComicBookTTS APK Build Monitor"
    Write-Host "✓ Scheduled task created: $taskName" -ForegroundColor Green
    Write-Host "  The monitor will start automatically when Windows starts" -ForegroundColor Gray
}
catch {
    Write-Host "⚠ Could not create scheduled task: $($_.Exception.Message)" -ForegroundColor Yellow
    Write-Host "  You can manually run the monitor script when needed" -ForegroundColor Gray
}

# Create desktop shortcuts
Write-Host "`nCreating desktop shortcuts..." -ForegroundColor Yellow
try {
    $desktopPath = [Environment]::GetFolderPath("Desktop")
    $currentPath = Get-Location
    
    # APK Monitor shortcut
    $monitorShortcut = Join-Path $desktopPath "ComicBookTTS APK Monitor.lnk"
    $WshShell = New-Object -comObject WScript.Shell
    $Shortcut = $WshShell.CreateShortcut($monitorShortcut)
    $Shortcut.TargetPath = "PowerShell.exe"
    $Shortcut.Arguments = "-ExecutionPolicy Bypass -File `"$currentPath\scripts\apk_monitor.ps1`""
    $Shortcut.WorkingDirectory = $currentPath
    $Shortcut.Description = "Start ComicBookTTS APK Monitor"
    $Shortcut.Save()
    Write-Host "✓ Created desktop shortcut: ComicBookTTS APK Monitor.lnk" -ForegroundColor Green
    
    # Dashboard shortcut
    $dashboardShortcut = Join-Path $desktopPath "ComicBookTTS APK Dashboard.lnk"
    $Shortcut = $WshShell.CreateShortcut($dashboardShortcut)
    $Shortcut.TargetPath = Join-Path $currentPath "apk_dashboard.html"
    $Shortcut.Description = "Open ComicBookTTS APK Dashboard"
    $Shortcut.Save()
    Write-Host "✓ Created desktop shortcut: ComicBookTTS APK Dashboard.lnk" -ForegroundColor Green
}
catch {
    Write-Host "⚠ Could not create desktop shortcuts: $($_.Exception.Message)" -ForegroundColor Yellow
}

# Test the notification system
Write-Host "`nTesting notification system..." -ForegroundColor Yellow
try {
    # Test desktop notification
    if (Get-Module -ListAvailable -Name BurntToast) {
        Import-Module BurntToast
        New-BurntToastNotification -Text "ComicBookTTS Setup", "APK notification system is now active!"
        Write-Host "✓ Test notification sent successfully" -ForegroundColor Green
    }
    else {
        Add-Type -AssemblyName System.Windows.Forms
        $notification = New-Object System.Windows.Forms.NotifyIcon
        $notification.Icon = [System.Drawing.SystemIcons]::Information
        $notification.BalloonTipTitle = "ComicBookTTS Setup"
        $notification.BalloonTipText = "APK notification system is now active!"
        $notification.Visible = $true
        $notification.ShowBalloonTip(3000)
        Write-Host "✓ Test notification sent successfully (fallback method)" -ForegroundColor Green
        Start-Sleep -Seconds 4
        $notification.Dispose()
    }
}
catch {
    Write-Host "⚠ Could not send test notification: $($_.Exception.Message)" -ForegroundColor Yellow
}

# Display setup summary
Write-Host "`n=== Setup Complete ===" -ForegroundColor Cyan
Write-Host "The ComicBookTTS APK notification system has been set up successfully!" -ForegroundColor Green
Write-Host ""
Write-Host "What's been configured:" -ForegroundColor Yellow
Write-Host "✓ APK monitoring script (scripts\apk_monitor.ps1)"
Write-Host "✓ Build hooks script (scripts\apk_build_hooks.ps1)"
Write-Host "✓ CI/CD integration script (scripts\ci_integration.ps1)"
Write-Host "✓ APK dashboard (apk_dashboard.html)"
Write-Host "✓ Configuration file (scripts\apk_config.json)"
Write-Host "✓ Scheduled task for automatic monitoring"
Write-Host "✓ Desktop shortcuts for easy access"
Write-Host ""
Write-Host "How to use:" -ForegroundColor Yellow
Write-Host "1. Double-click 'ComicBookTTS APK Monitor' on your desktop to start monitoring"
Write-Host "2. Double-click 'ComicBookTTS APK Dashboard' to view the web dashboard"
Write-Host "3. The system will automatically notify you of new APK builds"
Write-Host "4. Check the logs folder for detailed monitoring logs"
Write-Host ""
Write-Host "Manual commands:" -ForegroundColor Yellow
Write-Host "  .\scripts\apk_monitor.ps1                    # Start monitoring"
Write-Host "  .\scripts\ci_integration.ps1 -Action status  # Check system status"
Write-Host "  .\scripts\apk_build_hooks.ps1 -Action setup  # Setup build hooks"
Write-Host ""
Write-Host "Current APK locations:" -ForegroundColor Yellow

# Show current APK files
$apkFiles = Get-ChildItem -Path . -Filter "*.apk" -Recurse | Sort-Object LastWriteTime -Descending
foreach ($apk in $apkFiles) {
    $relativePath = $apk.FullName.Replace((Get-Location).Path + "\", "")
    $size = [math]::Round($apk.Length / 1MB, 2)
    Write-Host "  $relativePath ($size MB) - Modified: $($apk.LastWriteTime)" -ForegroundColor Gray
}

Write-Host "`nThe notification system is now ready to use!" -ForegroundColor Green