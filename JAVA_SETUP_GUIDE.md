# Java Setup Guide for ComicTTS

## Current Issue
The project requires Java 11+ for the Android Gradle Plugin, but the system currently has Java 8 and Java 24 installed. Java 24 is too new and causes compatibility issues with Gradle, while Java 8 is too old for modern Android development.

## Recommended Solution: Install Java 17 (LTS)

### Option 1: Install OpenJDK 17 (Recommended)

1. **Download OpenJDK 17**:
   - Visit: https://adoptium.net/temurin/releases/
   - Select: Java 17 (LTS)
   - Platform: Windows x64
   - Package Type: JDK
   - Download the `.msi` installer

2. **Install OpenJDK 17**:
   - Run the downloaded `.msi` file
   - Follow the installation wizard
   - Choose installation directory (e.g., `C:\Program Files\Eclipse Adoptium\jdk-17.0.x.x-hotspot\`)
   - Enable "Set JAVA_HOME variable" option
   - Enable "JavaSoft (Oracle) registry keys" option

3. **Verify Installation**:
   ```powershell
   java -version
   # Should show: openjdk version "17.0.x"
   ```

### Option 2: Use Microsoft Build of OpenJDK 17

1. **Download Microsoft OpenJDK 17**:
   - Visit: https://docs.microsoft.com/en-us/java/openjdk/download
   - Select: Java 17
   - Download the Windows x64 MSI

2. **Install and verify** (same steps as Option 1)

## Alternative: Use Java 8 with Older Android Gradle Plugin

If you cannot install Java 17, you can use the fallback configuration provided in this project:

### Fallback Configuration (Java 8 Compatible)
- Android Gradle Plugin: 7.4.2
- Gradle: 7.6
- Target SDK: 33
- Compile SDK: 33

To use the fallback configuration:
```powershell
# Set JAVA_HOME to Java 8
$env:JAVA_HOME="C:\Program Files\Java\jre1.8.0_461"

# Use the fallback build files
Copy-Item build-java8.gradle build.gradle
Copy-Item app\build-java8.gradle app\build.gradle
Copy-Item gradle\wrapper\gradle-wrapper-java8.properties gradle\wrapper\gradle-wrapper.properties

# Build the project
./gradlew clean build
```

## Environment Variables

After installing Java 17, ensure these environment variables are set:

```powershell
# Check current JAVA_HOME
echo $env:JAVA_HOME

# Set JAVA_HOME (if not set automatically)
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.x.x-hotspot"

# Add to PATH (if not added automatically)
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
```

## Troubleshooting

### Issue: "Unsupported class file major version"
- **Cause**: Gradle daemon is using wrong Java version
- **Solution**: 
  ```powershell
  ./gradlew --stop
  # Clear Gradle cache
  Remove-Item -Recurse -Force $env:USERPROFILE\.gradle\daemon
  # Restart with correct Java version
  ./gradlew clean build
  ```

### Issue: "Could not resolve Android Gradle Plugin"
- **Cause**: Java version incompatibility
- **Solution**: Use Java 17 or the fallback configuration

### Issue: Build works but app crashes
- **Cause**: Runtime Java version mismatch
- **Solution**: Ensure Android device/emulator uses compatible runtime

## Next Steps

1. Install Java 17 using one of the options above
2. Verify installation with `java -version`
3. Run `./gradlew clean build` to test the build
4. If successful, proceed with development
5. If issues persist, use the fallback configuration

## Project Configuration Summary

**Current Configuration (Requires Java 17+)**:
- Android Gradle Plugin: 8.1.4
- Gradle: 8.4
- Java: 17
- Target SDK: 34
- Compile SDK: 34

**Fallback Configuration (Java 8 Compatible)**:
- Android Gradle Plugin: 7.4.2
- Gradle: 7.6
- Java: 8
- Target SDK: 33
- Compile SDK: 33
