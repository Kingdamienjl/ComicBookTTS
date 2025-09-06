#!/usr/bin/env pwsh
# ComicBookTTS - Cleanup Script
# This script cleans up temporary files, build artifacts, and cache directories

param(
    [string]$ProjectPath = ".",
    [switch]$DeepClean,
    [switch]$Verbose,
    [switch]$DryRun
)

# Set error action preference
$ErrorActionPreference = "Stop"

# Enable verbose output if requested
if ($Verbose) {
    $VerbosePreference = "Continue"
}

Write-Host "🧹 ComicBookTTS Cleanup Script" -ForegroundColor Cyan
Write-Host "=================================" -ForegroundColor Cyan

if ($DryRun) {
    Write-Host "🔍 DRY RUN MODE - No files will be deleted" -ForegroundColor Yellow
    Write-Host "" 
}

# Function to log messages
function Write-Log {
    param(
        [string]$Message,
        [string]$Level = "INFO"
    )
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $color = switch ($Level) {
        "ERROR" { "Red" }
        "WARN" { "Yellow" }
        "SUCCESS" { "Green" }
        "DELETED" { "Magenta" }
        default { "White" }
    }
    Write-Host "[$timestamp] [$Level] $Message" -ForegroundColor $color
}

# Function to safely remove items
function Remove-SafeItem {
    param(
        [string]$Path,
        [string]$Description = ""
    )
    
    if (Test-Path $Path) {
        try {
            $item = Get-Item $Path
            $size = if ($item.PSIsContainer) {
                $childItems = Get-ChildItem $Path -Recurse -Force -ErrorAction SilentlyContinue
                $totalSize = ($childItems | Where-Object { -not $_.PSIsContainer } | Measure-Object -Property Length -Sum).Sum
                if ($totalSize) { "$([math]::Round($totalSize / 1MB, 2)) MB" } else { "0 MB" }
            } else {
                "$([math]::Round($item.Length / 1MB, 2)) MB"
            }
            
            if ($DryRun) {
                Write-Log "Would delete: $Path ($size) - $Description" "INFO"
            } else {
                Remove-Item $Path -Recurse -Force -ErrorAction Stop
                Write-Log "Deleted: $Path ($size) - $Description" "DELETED"
            }
            return $true
        } catch {
            Write-Log "Failed to delete $Path : $($_.Exception.Message)" "ERROR"
            return $false
        }
    } else {
        Write-Verbose "Path not found: $Path"
        return $false
    }
}

try {
    # Navigate to project directory
    Write-Log "Cleaning project in: $ProjectPath" "INFO"
    Set-Location $ProjectPath

    $deletedCount = 0
    $totalSaved = 0

    # Standard cleanup items
    $cleanupItems = @(
        @{ Path = "build"; Description = "Gradle build directory" },
        @{ Path = "app/build"; Description = "App build directory" },
        @{ Path = ".gradle"; Description = "Gradle cache directory" },
        @{ Path = "logs"; Description = "Application logs" },
        @{ Path = "temp"; Description = "Temporary files" },
        @{ Path = "cache"; Description = "Application cache" },
        @{ Path = "*.log"; Description = "Log files" },
        @{ Path = "*.tmp"; Description = "Temporary files" },
        @{ Path = "*.bak"; Description = "Backup files" },
        @{ Path = "*~"; Description = "Editor backup files" },
        @{ Path = ".DS_Store"; Description = "macOS system files" },
        @{ Path = "Thumbs.db"; Description = "Windows thumbnail cache" },
        @{ Path = "desktop.ini"; Description = "Windows desktop configuration" }
    )

    # Deep clean items (only if DeepClean is specified)
    if ($DeepClean) {
        Write-Log "Deep clean mode enabled" "WARN"
        $cleanupItems += @(
            @{ Path = "app/src/main/assets/temp"; Description = "Asset temporary files" },
            @{ Path = "app/src/main/res/drawable-*dpi"; Description = "Generated drawable resources" },
            @{ Path = "*.apk"; Description = "APK files" },
            @{ Path = "*.aab"; Description = "Android App Bundle files" },
            @{ Path = "local.properties"; Description = "Local SDK configuration" },
            @{ Path = "config.properties"; Description = "User configuration (WARNING: Contains your settings!)" }
        )
    }

    Write-Log "Starting cleanup process..." "INFO"
    Write-Log "" "INFO"

    # Process each cleanup item
    foreach ($item in $cleanupItems) {
        $path = $item.Path
        $description = $item.Description
        
        # Handle wildcard patterns
        if ($path.Contains("*")) {
            $matchingItems = Get-ChildItem -Path $path -Force -ErrorAction SilentlyContinue
            foreach ($matchingItem in $matchingItems) {
                if (Remove-SafeItem -Path $matchingItem.FullName -Description $description) {
                    $deletedCount++
                }
            }
        } else {
            if (Remove-SafeItem -Path $path -Description $description) {
                $deletedCount++
            }
        }
    }

    # Clean Gradle daemon (if requested)
    if ($DeepClean) {
        Write-Log "" "INFO"
        Write-Log "Stopping Gradle daemon..." "INFO"
        try {
            if ($IsWindows) {
                & .\gradlew.bat --stop 2>$null
            } else {
                & ./gradlew --stop 2>/dev/null
            }
            Write-Log "Gradle daemon stopped" "SUCCESS"
        } catch {
            Write-Log "Could not stop Gradle daemon (may not be running)" "WARN"
        }
    }

    # Summary
    Write-Log "" "INFO"
    if ($DryRun) {
        Write-Log "🔍 Dry run completed - $deletedCount items would be deleted" "SUCCESS"
        Write-Log "Run without -DryRun to actually delete files" "INFO"
    } else {
        Write-Log "🎉 Cleanup completed - $deletedCount items deleted" "SUCCESS"
    }
    
    Write-Log "" "INFO"
    Write-Log "Cleanup recommendations:" "INFO"
    Write-Log "• Run 'gradlew clean' for a fresh build" "INFO"
    Write-Log "• Clear Android Studio caches if issues persist" "INFO"
    Write-Log "• Use -DeepClean for more thorough cleanup" "INFO"
    
    if ($DeepClean) {
        Write-Log "" "INFO"
        Write-Log "⚠️  Deep clean completed. You may need to:" "WARN"
        Write-Log "• Reconfigure your settings in config.properties" "WARN"
        Write-Log "• Re-sync the project in Android Studio" "WARN"
    }

} catch {
    Write-Log "Cleanup failed: $($_.Exception.Message)" "ERROR"
    Write-Log "Please check the error above and try again" "ERROR"
    exit 1
}

Write-Host "" 
Write-Host "Cleanup script completed! 🧹✨" -ForegroundColor Green
Write-Host "Your project is now clean and ready for a fresh build." -ForegroundColor Green