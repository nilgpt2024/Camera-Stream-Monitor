@echo off
chcp 65001 >nul
echo ============================================
echo   Video Monitor - Final Build Solution
echo ============================================
echo.
echo [STATUS] Current Java Version:
java -version 2>&1 | findstr /i "version"
echo.
echo [PROBLEM] AGP 7.4.2 requires Java 11+, but you have Java 8
echo.
echo ============================================
echo   SOLUTION OPTIONS
echo ============================================
echo.
echo Option 1: Use Android Studio (EASIEST)
echo   → Open Android Studio
echo   → File → Open → F:\android\andwin
echo   → Click "Sync Project with Gradle Files"
echo   → Run app (▶️ button)
echo.
echo Option 2: Install JDK 17 (RECOMMENDED)
echo   → Download: https://adoptium.net/temurin/releases/?version=17
echo   → Choose: Windows x64 .msi installer
echo   → Install JDK 17 (will update JAVA_HOME automatically)
echo   → Then run: gradlew.bat assembleDebug
echo.
echo Option 3: Manual fix (ADVANCED)
echo   → Set JAVA_HOME to JDK 11+ path
echo   → Or create jvm.cfg for Android Studio JDK
echo.
pause
