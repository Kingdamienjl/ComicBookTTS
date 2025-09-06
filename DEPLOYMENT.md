# ComicBookTTS Deployment Guide

This guide explains how to set up automated deployment for the ComicBookTTS Android application using GitHub Actions and Vercel.

## 🚀 Quick Start

### Prerequisites

- GitHub repository with the ComicBookTTS code
- Vercel account (free tier available)
- Android development environment (for local builds)

### 1. Vercel Setup

1. **Create Vercel Account**
   - Go to [vercel.com](https://vercel.com) and sign up
   - Connect your GitHub account

2. **Import Project**
   - Click "New Project" in Vercel dashboard
   - Import your ComicBookTTS repository
   - Use the following settings:
     - Framework Preset: `Other`
     - Root Directory: `./`
     - Build Command: `echo "Static deployment"`
     - Output Directory: `./`

3. **Get Vercel Tokens**
   - Go to Vercel Settings → Tokens
   - Create a new token with appropriate scope
   - Note down your Organization ID and Project ID from project settings

### 2. GitHub Secrets Configuration

Add the following secrets to your GitHub repository:

**Repository Settings → Secrets and Variables → Actions**

#### Required Secrets

```bash
# Vercel Configuration
VERCEL_TOKEN=your_vercel_token_here
VERCEL_ORG_ID=your_org_id_here
VERCEL_PROJECT_ID=your_project_id_here
```

#### Optional Secrets (for APK signing)

```bash
# Android App Signing (Optional)
KEYSTORE_FILE=base64_encoded_keystore_file
KEYSTORE_PASSWORD=your_keystore_password
KEY_ALIAS=your_key_alias
KEY_PASSWORD=your_key_password
```

### 3. Workflow Configuration

The repository includes two main workflows:

#### CI/CD Pipeline (`.github/workflows/ci-cd.yml`)

- **Triggers**: Push to `main`/`develop`, Pull Requests
- **Jobs**:
  - 🧪 **Test**: Runs unit tests with coverage
  - 🔨 **Build**: Creates debug and release APKs
  - 🚀 **Deploy**: Deploys to Vercel (main branch only)
  - 🔒 **Security**: Vulnerability scanning
  - 📊 **Quality**: Code quality analysis

#### Release Workflow (`.github/workflows/release.yml`)

- **Triggers**: Git tags (`v*.*.*`), Manual dispatch
- **Features**:
  - 📦 Creates GitHub releases
  - 🔐 Signs APKs (if configured)
  - 📝 Generates changelogs
  - 🌐 Deploys to production

## 📱 Deployment Process

### Automatic Deployment

1. **Development Deployment**
   ```bash
   git push origin develop
   ```
   - Runs tests and builds APK
   - No deployment to production

2. **Production Deployment**
   ```bash
   git push origin main
   ```
   - Runs full CI/CD pipeline
   - Deploys to Vercel if tests pass

3. **Release Deployment**
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
   - Creates GitHub release
   - Builds signed APK
   - Deploys to production

### Manual Deployment

1. **Local Vercel Deployment**
   ```bash
   npm install -g vercel
   vercel login
   vercel --prod
   ```

2. **Local APK Build**
   ```bash
   ./gradlew assembleRelease
   ```

## 🔧 Configuration Files

### `vercel.json`

Configures Vercel deployment:
- Static file serving for APK downloads
- Landing page routing
- Environment variables
- Build settings

### `index.html`

Landing page features:
- 📱 Direct APK download link
- 🎨 Modern, responsive design
- 📋 Feature highlights
- 📊 App information

## 🛠️ Advanced Configuration

### Custom Build Scripts

1. **Pre-build Setup**
   ```bash
   # Add to workflow before build
   - name: Custom setup
     run: |
       echo "custom_config=true" >> config.properties
   ```

2. **Post-build Actions**
   ```bash
   # Add after APK build
   - name: Custom post-build
     run: |
       # Upload to custom storage
       # Send notifications
       # Update documentation
   ```

### Environment-Specific Deployments

```yaml
# Add to workflow
strategy:
  matrix:
    environment: [staging, production]
steps:
  - name: Deploy to ${{ matrix.environment }}
    run: vercel --prod=${{ matrix.environment == 'production' }}
```

### APK Signing Setup

1. **Generate Keystore**
   ```bash
   keytool -genkey -v -keystore release-key.jks \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -alias comicbook-tts
   ```

2. **Encode for GitHub Secrets**
   ```bash
   base64 -i release-key.jks | pbcopy
   ```

3. **Add to GitHub Secrets**
   - `KEYSTORE_FILE`: Base64 encoded keystore
   - `KEYSTORE_PASSWORD`: Keystore password
   - `KEY_ALIAS`: Key alias name
   - `KEY_PASSWORD`: Key password

## 📊 Monitoring & Analytics

### GitHub Actions Monitoring

- **Workflow Status**: Repository → Actions tab
- **Build Logs**: Click on individual workflow runs
- **Artifacts**: Download APKs and test reports

### Vercel Analytics

- **Deployment Status**: Vercel dashboard
- **Performance Metrics**: Built-in analytics
- **Error Tracking**: Function logs and errors

### Custom Monitoring

```yaml
# Add to workflow for custom monitoring
- name: Send deployment notification
  run: |
    curl -X POST "$WEBHOOK_URL" \
      -H "Content-Type: application/json" \
      -d '{"status": "deployed", "version": "${{ github.ref_name }}"}'
  env:
    WEBHOOK_URL: ${{ secrets.WEBHOOK_URL }}
```

## 🔍 Troubleshooting

### Common Issues

1. **Build Failures**
   - Check Java version compatibility
   - Verify Android SDK setup
   - Review dependency conflicts

2. **Deployment Failures**
   - Verify Vercel tokens and IDs
   - Check file permissions
   - Review network connectivity

3. **Test Failures**
   - Update test dependencies
   - Check Robolectric configuration
   - Verify mock setups

### Debug Commands

```bash
# Local debugging
./gradlew build --debug
./gradlew testDebugUnitTest --info
vercel --debug

# GitHub Actions debugging
# Add to workflow:
- name: Debug info
  run: |
    echo "Java version: $(java -version)"
    echo "Android SDK: $ANDROID_HOME"
    echo "Gradle version: $(./gradlew --version)"
```

## 📚 Additional Resources

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Vercel Documentation](https://vercel.com/docs)
- [Android App Bundle Guide](https://developer.android.com/guide/app-bundle)
- [Gradle Build Guide](https://docs.gradle.org/current/userguide/userguide.html)

## 🤝 Contributing

To contribute to the deployment configuration:

1. Fork the repository
2. Create a feature branch
3. Test changes in your fork
4. Submit a pull request

---

**Need Help?** Open an issue in the GitHub repository with the `deployment` label.