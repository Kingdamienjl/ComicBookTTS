#!/usr/bin/env pwsh
# ComicBookTTS - GitHub Setup Script
# This script automates the setup process for the ComicBookTTS project

param(
    [string]$ProjectPath = ".",
    [switch]$SkipDependencies,
    [switch]$Verbose
)

# Set error action preference
$ErrorActionPreference = "Stop"

# Enable verbose output if requested
if ($Verbose) {
    $VerbosePreference = "Continue"
}

Write-Host "🚀 ComicBookTTS GitHub Setup Script" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

# Function to check if command exists
function Test-Command {
    param([string]$Command)
    try {
        Get-Command $Command -ErrorAction Stop | Out-Null
        return $true
    } catch {
        return $false
    }
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
        default { "White" }
    }
    Write-Host "[$timestamp] [$Level] $Message" -ForegroundColor $color
}

try {
    # Step 1: Verify Java installation
    Write-Log "Checking Java installation..." "INFO"
    if (Test-Command "java") {
        $javaVersion = java -version 2>&1 | Select-String "openjdk version" | ForEach-Object { $_.ToString() }
        Write-Log "Found Java: $javaVersion" "SUCCESS"
        
        # Check if Java 17 is available
        if ($javaVersion -match "17\.") {
            Write-Log "Java 17 detected - Compatible!" "SUCCESS"
        } else {
            Write-Log "Java 17 not detected. Please install Java 17 LTS" "WARN"
            Write-Log "Download from: https://adoptium.net/temurin/releases/" "INFO"
        }
    } else {
        Write-Log "Java not found. Please install Java 17 LTS" "ERROR"
        throw "Java installation required"
    }

    # Step 2: Verify Android SDK (if available)
    Write-Log "Checking Android SDK..." "INFO"
    if ($env:ANDROID_HOME -or $env:ANDROID_SDK_ROOT) {
        $androidHome = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
        Write-Log "Android SDK found at: $androidHome" "SUCCESS"
    } else {
        Write-Log "Android SDK not configured. Set ANDROID_HOME environment variable" "WARN"
    }

    # Step 3: Navigate to project directory
    Write-Log "Setting up project in: $ProjectPath" "INFO"
    Set-Location $ProjectPath

    # Step 4: Create config.properties from template
    Write-Log "Setting up configuration..." "INFO"
    if (Test-Path "config.properties.template") {
        if (-not (Test-Path "config.properties")) {
            Copy-Item "config.properties.template" "config.properties"
            Write-Log "Created config.properties from template" "SUCCESS"
            Write-Log "Please edit config.properties to configure your settings" "INFO"
        } else {
            Write-Log "config.properties already exists" "INFO"
        }
    } else {
        Write-Log "config.properties.template not found" "WARN"
    }

    # Step 5: Set up Gradle wrapper permissions (if on Unix-like system)
    if (Test-Path "gradlew") {
        Write-Log "Gradle wrapper found" "SUCCESS"
        if ($IsLinux -or $IsMacOS) {
            chmod +x gradlew
            Write-Log "Set execute permissions on gradlew" "SUCCESS"
        }
    }

    # Step 6: Clean and build project (if not skipping dependencies)
    if (-not $SkipDependencies) {
        Write-Log "Building project..." "INFO"
        
        # Clean build
        Write-Log "Cleaning previous builds..." "INFO"
        if ($IsWindows) {
            & .\gradlew.bat clean
        } else {
            & ./gradlew clean
        }
        
        if ($LASTEXITCODE -eq 0) {
            Write-Log "Clean completed successfully" "SUCCESS"
        } else {
            Write-Log "Clean failed with exit code $LASTEXITCODE" "ERROR"
        }

        # Build project
        Write-Log "Building project..." "INFO"
        if ($IsWindows) {
            & .\gradlew.bat build
        } else {
            & ./gradlew build
        }
        
        if ($LASTEXITCODE -eq 0) {
            Write-Log "Build completed successfully" "SUCCESS"
        } else {
            Write-Log "Build failed with exit code $LASTEXITCODE" "WARN"
            Write-Log "This might be normal for initial setup. Check dependencies." "INFO"
        }
    } else {
        Write-Log "Skipping dependency installation and build" "INFO"
    }

    # Step 7: Create necessary directories
    Write-Log "Creating project directories..." "INFO"
    $directories = @(
        "logs",
        "temp",
        "cache"
    )
    
    foreach ($dir in $directories) {
        if (-not (Test-Path $dir)) {
            New-Item -ItemType Directory -Path $dir -Force | Out-Null
            Write-Log "Created directory: $dir" "SUCCESS"
        }
    }

    # Step 8: Setup complete
    Write-Log "" "INFO"
    Write-Log "🎉 Setup completed successfully!" "SUCCESS"
    Write-Log "" "INFO"
    Write-Log "Next steps:" "INFO"
    Write-Log "1. Edit config.properties to configure your TTS settings" "INFO"
    Write-Log "2. Open the project in Android Studio" "INFO"
    Write-Log "3. Sync Gradle and run the app" "INFO"
    Write-Log "" "INFO"
    Write-Log "For troubleshooting, check the README.md file" "INFO"

} catch {
    Write-Log "Setup failed: $($_.Exception.Message)" "ERROR"
    Write-Log "Please check the error above and try again" "ERROR"
    exit 1
}

Write-Host "" 
Write-Host "Setup script completed. Happy coding! 🚀" -ForegroundColor Green