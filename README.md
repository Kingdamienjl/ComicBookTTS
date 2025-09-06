# ComicBookTTS

A powerful Android comic book reader that combines OCR text extraction with high-quality Text-to-Speech narration using Android's advanced TTS engine.

## 🌟 Features

- 📚 **Comic Support**: Read `.CBR` and `.CBZ` comic files with efficient archive extraction
- 🔍 **Advanced OCR**: Enhanced text extraction using Google ML Kit with adaptive preprocessing
- 🎙️ **Premium TTS**: High-quality voice narration via Android's built-in TTS with comic-optimized speech patterns
- 📖 **Intuitive Navigation**: Smooth swipe and zoom controls with PhotoView integration
- 🔊 **Auto-play Mode**: Automatic page progression with intelligent TTS timing
- 🎯 **Smart Text Processing**: Optimized extraction with caching, noise reduction, and adaptive thresholding
- ⚡ **Performance Optimized**: Advanced image preprocessing with histogram equalization and edge enhancement
- 🔒 **Secure**: No hardcoded API keys, comprehensive error handling and logging
- 🚀 **CI/CD Ready**: Automated testing and deployment with GitHub Actions and Vercel

## 🚀 Quick Start

### Prerequisites

- **Android Studio** (latest version recommended)
- **Java 17 LTS** (recommended) or **Java 21+** (compatible with Gradle 8.10.2)
- **Android SDK** (API level 24+, targeting API 34)
- **Android TTS Engine** (pre-installed on most devices)
- **Git** (for version control and CI/CD)

### 1. Clone and Setup

```bash
git clone <repository-url>
cd ComicBookTTS
```

### 2. Configure TTS Settings

1. **Android TTS Setup**:
   - Ensure Android TTS is enabled in device settings
   - Download additional voice packs if desired
   - Configure preferred voice and speech rate

2. **App Configuration**:
   - Launch the app
   - Go to Settings (menu → Settings)
   - Adjust TTS speed, pitch, and voice preferences
   - Test voice output

### 3. Build and Run

```bash
./gradlew build
./gradlew installDebug
```

## 🔧 Development Setup

### Java Version Compatibility

This project supports:
- ✅ **Java 17 LTS** (Recommended for stability)
- ✅ **Java 21** (Latest LTS)
- ✅ **Java 24** (Latest)
- ❌ Java 11 or earlier (Not supported)

### Android SDK Requirements

- **Minimum SDK**: API 24 (Android 7.0)
- **Target SDK**: API 34 (Android 14)
- **Compile SDK**: API 34

### Dependencies

Key dependencies include:
- **ML Kit Text Recognition**: OCR functionality
- **OkHttp**: API communication
- **ViewPager2**: Page navigation
- **PhotoView**: Image zoom capabilities
- **Zip4j & JunRAR**: Archive extraction

## 🧪 Testing

### Run Unit Tests
```bash
./gradlew test
```

### Run Instrumented Tests
```bash
./gradlew connectedAndroidTest
```

### Test Coverage
- **OCR Components**: Text extraction, image processing, and adaptive preprocessing
- **TTS Integration**: Android TTS communication and error handling
- **Performance**: Caching systems and memory optimization
- **Security**: Configuration validation and secure storage
- **Integration**: End-to-end workflow testing with Robolectric

## 🔒 Security Best Practices

### API Key Management
- ✅ **Never commit API keys** to version control
- ✅ **Use app settings** for user configuration
- ✅ **Validate keys** before API calls
- ✅ **Provide clear error messages** for missing keys

### Files Excluded from Git
```
config.properties
*.key
*.pem
keystore.properties
app/google-services.json
```

## 📱 Usage

### Basic Workflow
1. **Open Comic**: Tap "Open Comic" and select a `.cbr` or `.cbz` file
2. **Navigate**: Swipe left/right to change pages
3. **Extract Text**: Tap "Play TTS" to extract and read page text
4. **Auto-play**: Enable auto-play for continuous reading

### Settings Configuration
- **Voice Settings**: Configure Android TTS voice preferences
- **Voice Settings**: Customize voice parameters
- **OCR Options**: Adjust text recognition settings

## 🏗️ Architecture

### Core Components

#### OCR System (`TextRecognitionHelper`)
- **Google ML Kit Integration**: Advanced text recognition with ML Kit
- **Adaptive Preprocessing**: Dynamic image analysis and optimization strategies
- **Performance Caching**: Intelligent caching system for preprocessing and image analysis
- **Image Enhancement**: Histogram equalization, noise reduction, and adaptive thresholding
- **Memory Optimization**: Efficient bitmap processing with automatic scaling
- **Error Handling**: Comprehensive error tracking and graceful failure management
- **Performance Monitoring**: Real-time metrics and cache statistics
- **Async Processing**: Non-blocking text extraction with progress callbacks

#### TTS System (`HybridTTS`)
- **Android Native TTS**: Advanced voice synthesis with comic-optimized patterns
- **Streaming Audio**: Real-time audio generation
- **MediaPlayer Integration**: Smooth audio playback
- **Callback System**: Event-driven architecture

#### Archive Extraction (`ComicArchiveExtractor`)
- **Multi-format Support**: CBR, CBZ, ZIP, RAR
- **Efficient Extraction**: Temporary file management
- **Image Filtering**: Automatic image file detection

### Data Flow
```
Comic File → Archive Extraction → Image Pages → OCR Processing → Text → Android TTS → Audio Playback
```

## 🚀 Deployment & CI/CD

### Automated Deployment
This project includes comprehensive CI/CD pipelines:

#### GitHub Actions Workflows
- **CI/CD Pipeline** (`.github/workflows/ci-cd.yml`):
  - Automated testing on push/PR
  - Debug and release APK builds
  - Vercel deployment for main branch
  - Security scanning with Trivy
  - Code quality analysis with Android Lint

- **Release Pipeline** (`.github/workflows/release.yml`):
  - Automated releases on tag push
  - Signed APK generation
  - GitHub release creation with changelogs
  - Vercel deployment for releases

#### Vercel Integration
- **Static Hosting**: Landing page with download links
- **APK Distribution**: Direct APK download via `/download` route
- **Automatic Deployment**: Triggered by GitHub Actions

### Local Deployment
Use the provided PowerShell script for local deployment:
```powershell
.\deploy.ps1
```

This script will:
1. Clean previous builds
2. Run tests
3. Build APK
4. Deploy to Vercel (if authenticated)

### Setup Requirements
For deployment, configure these GitHub secrets:
- `VERCEL_TOKEN`: Vercel authentication token
- `VERCEL_ORG_ID`: Your Vercel organization ID
- `VERCEL_PROJECT_ID`: Your Vercel project ID

See `DEPLOYMENT.md` for detailed setup instructions.

## 🔧 Configuration Options

### Android TTS Settings
```kotlin
// Default Android TTS configuration
speech_rate = 1.0f        // Normal speed
pitch = 1.0f              // Normal pitch
voice_engine = "com.google.android.tts"  // Default TTS engine
language = Locale.getDefault()  // System language
```

### OCR Settings
```kotlin
// Image processing limits
max_image_dimension = 2048
enable_preprocessing = true
```

## 🐛 Troubleshooting

### Common Issues

#### Build Failures
- **Java Version**: Ensure Java 21+ is installed
- **Gradle Version**: Use Gradle 8.10.2 or later
- **Android SDK**: Verify SDK components are installed

#### TTS Issues
- **Voice Issues**: Check available TTS voices in Android Settings → Accessibility → Text-to-Speech
- **Audio Problems**: Verify device audio output and volume levels
- **Engine Problems**: Try different TTS engines in Android settings (Google, Samsung, etc.)
- **Language Support**: Download additional language packs from TTS settings
- **Performance**: Clear TTS cache or restart the TTS service

#### OCR Problems
- **No Text Found**: Ensure image contains readable text with sufficient contrast
- **Memory Issues**: Large images are automatically scaled and cached
- **Language Support**: ML Kit supports Latin-based languages and many others
- **Performance**: Check cache statistics and clear preprocessing cache if needed
- **Image Quality**: Poor quality images benefit from adaptive preprocessing

### Debug Logging
Enable detailed logging in `HybridTTS` and `TextRecognitionHelper` for troubleshooting:

```kotlin
// Enable debug logging
Log.d("ComicTTS", "Debug information")

// Check performance metrics
val metrics = textRecognitionHelper.getPerformanceMetrics()
Log.d("OCR_Performance", "Cache size: ${metrics.preprocessingCacheSize}")

// Monitor cache statistics
val cacheStats = textRecognitionHelper.getCacheStatistics()
Log.d("Cache_Stats", "Hit rate: ${cacheStats.hitRate}")
```

#### Deployment Issues
- **Vercel Authentication**: Run `npx vercel login` to authenticate
- **GitHub Actions**: Check repository secrets are properly configured
- **APK Build**: Ensure Java 17+ and Android SDK are properly installed
- **CI/CD Failures**: Review workflow logs in GitHub Actions tab

## 🤝 Contributing

### Development Guidelines
1. **Security First**: Never commit sensitive data
2. **Test Coverage**: Add tests for new features
3. **Code Style**: Follow Kotlin conventions
4. **Documentation**: Update README for changes

### Pull Request Process
1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Update documentation
5. Submit pull request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🙏 Acknowledgments

- **Google ML Kit**: OCR functionality
- **Android TTS**: Native text-to-speech engine
- **Android Community**: Development resources and libraries

## 📞 Support

For issues and questions:
1. Check the troubleshooting section
2. Review existing GitHub issues
3. Create a new issue with detailed information

---

**Note**: This app uses Android's built-in TTS engine. Additional voices can be downloaded from device settings for enhanced experience.