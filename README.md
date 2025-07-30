# ComicBookTTS

A powerful Android comic book reader that combines OCR text extraction with high-quality Text-to-Speech narration using ElevenLabs API.

## 🌟 Features

- 📚 **Comic Support**: Read `.CBR` and `.CBZ` comic files
- 🔍 **OCR Integration**: Extract dialogue using Google ML Kit OCR
- 🎙️ **Premium TTS**: High-quality voice narration via ElevenLabs API
- 📖 **Intuitive Navigation**: Swipe and zoom controls
- 🔊 **Auto-play Mode**: Automatic page progression with TTS
- 🎯 **Smart Text Processing**: Optimized text extraction and processing
- 🔒 **Secure**: No hardcoded API keys, user-controlled configuration

## 🚀 Quick Start

### Prerequisites

- **Android Studio** (latest version recommended)
- **Java 21** or **Java 24** (compatible with Gradle 8.10.2)
- **Android SDK** (API level 24+)
- **ElevenLabs Account** (for TTS functionality)

### 1. Clone and Setup

```bash
git clone <repository-url>
cd ComicBookTTS
```

### 2. Configure API Key

1. **Get ElevenLabs API Key**:
   - Visit [ElevenLabs.io](https://elevenlabs.io)
   - Sign up for an account
   - Navigate to Profile & API Key
   - Copy your API key

2. **Configure in App**:
   - Launch the app
   - Go to Settings (menu → Settings)
   - Paste your API key
   - Test the connection

### 3. Build and Run

```bash
./gradlew build
./gradlew installDebug
```

## 🔧 Development Setup

### Java Version Compatibility

This project supports:
- ✅ **Java 21** (Recommended)
- ✅ **Java 24** (Latest)
- ❌ Java 17 or earlier (Not supported)

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
- **OCR Components**: Text extraction and image processing
- **TTS Integration**: API communication and error handling
- **Security**: API key validation and storage

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
- **API Key**: Set your ElevenLabs API key
- **Voice Settings**: Customize voice parameters
- **OCR Options**: Adjust text recognition settings

## 🏗️ Architecture

### Core Components

#### OCR System (`TextRecognitionHelper`)
- **Google ML Kit Integration**: Advanced text recognition
- **Image Optimization**: Memory-efficient bitmap processing
- **Error Handling**: Graceful failure management
- **Async Processing**: Non-blocking text extraction

#### TTS System (`ElevenLabsTTS`)
- **ElevenLabs API**: Premium voice synthesis
- **Streaming Audio**: Real-time audio generation
- **MediaPlayer Integration**: Smooth audio playback
- **Callback System**: Event-driven architecture

#### Archive Extraction (`ComicArchiveExtractor`)
- **Multi-format Support**: CBR, CBZ, ZIP, RAR
- **Efficient Extraction**: Temporary file management
- **Image Filtering**: Automatic image file detection

### Data Flow
```
Comic File → Archive Extraction → Image Pages → OCR Processing → Text → TTS API → Audio Playback
```

## 🔧 Configuration Options

### Voice Settings
```kotlin
// Default ElevenLabs configuration
voice_id = "IRHApOXLvnW57QJPQH2P"
model = "eleven_monolingual_v1"
stability = 0.5
similarity_boost = 0.75
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

#### API Issues
- **Invalid Key**: Check ElevenLabs API key format
- **Network Errors**: Verify internet connection
- **Rate Limits**: Check ElevenLabs usage quotas

#### OCR Problems
- **No Text Found**: Ensure image contains readable text
- **Memory Issues**: Large images are automatically scaled
- **Language Support**: ML Kit supports Latin-based languages

### Debug Logging
Enable detailed logging in `ElevenLabsTTS` and `TextRecognitionHelper` for troubleshooting.

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
- **ElevenLabs**: Premium TTS services
- **Android Community**: Development resources and libraries

## 📞 Support

For issues and questions:
1. Check the troubleshooting section
2. Review existing GitHub issues
3. Create a new issue with detailed information

---

**Note**: This app requires an active ElevenLabs subscription for TTS functionality. Free tier limits may apply.