@echo off
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.16.8-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
echo Using Java 17: %JAVA_HOME%
java -version
echo.
echo Building with Java 17...
if "%1"=="notest" (
    gradlew.bat clean assembleDebug assembleRelease -x test -x testDebugUnitTest -x testReleaseUnitTest
) else (
    gradlew.bat clean build %*
)
