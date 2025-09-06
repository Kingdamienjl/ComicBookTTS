#!/usr/bin/env pwsh
# ComicBookTTS Deployment Script
# This script builds the Android APK and deploys to Vercel

Write-Host "🚀 Starting ComicBookTTS deployment process..." -ForegroundColor Green

# Step 1: Clean previous builds
Write-Host "🧹 Cleaning previous builds..." -ForegroundColor Yellow
./gradlew clean

# Step 2: Run tests
Write-Host "🧪 Running tests..." -ForegroundColor Yellow
./gradlew testDebugUnitTest
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Tests failed! Deployment aborted." -ForegroundColor Red
    exit 1
}

# Step 3: Build APK
Write-Host "🔨 Building APK..." -ForegroundColor Yellow
./gradlew assembleDebug
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Build failed! Deployment aborted." -ForegroundColor Red
    exit 1
}

# Step 4: Copy APK to root for Vercel
Write-Host "📦 Preparing APK for deployment..." -ForegroundColor Yellow
Copy-Item "app\build\outputs\apk\debug\app-debug.apk" ".\ComicBookTTS.apk" -Force

# Step 5: Deploy to Vercel (if authenticated)
Write-Host "🌐 Deploying to Vercel..." -ForegroundColor Yellow
try {
    npx vercel --prod --yes
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ Deployment successful!" -ForegroundColor Green
        Write-Host "📱 Your app is now available for download at the deployed URL" -ForegroundColor Cyan
    } else {
        Write-Host "⚠️  Vercel deployment failed. Please check your authentication and project settings." -ForegroundColor Yellow
        Write-Host "💡 Run 'npx vercel login' to authenticate with Vercel" -ForegroundColor Cyan
    }
} catch {
    Write-Host "⚠️  Vercel deployment failed: $($_.Exception.Message)" -ForegroundColor Yellow
    Write-Host "💡 Make sure you're logged in to Vercel: npx vercel login" -ForegroundColor Cyan
}

Write-Host "🎉 Deployment process completed!" -ForegroundColor Green