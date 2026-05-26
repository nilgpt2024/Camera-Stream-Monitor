@echo off
chcp 65001 >nul
echo ============================================
echo   Video Monitor - Build Script v2.0
echo ============================================
echo.
echo This script will:
echo   1. Reset JAVA_HOME to system Java 8
echo   2. Try to build (will fail if AGP needs Java 11+)
echo   3. Show you what to do next
echo.

echo [INFO] Current JAVA_HOME: %JAVA_HOME%
echo.

REM Option: If you have JDK 11+ installed, change this path
set "NEW_JAVA_HOME=C:\Program Files\Java\jdk1.8.0_291"

echo [ACTION] Setting JAVA_HOME to: %NEW_JAVA_HOME%
set "JAVA_HOME=%NEW_JAVA_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo [VERIFY] Java version:
java -version 2>&1 | findstr /i "version"
echo.

echo [BUILD] Starting Gradle...
cd /d "%~dp0"
call gradlew.bat assembleDebug --no-daemon --info 2>&1 | findstr /i "error failed success Java requires"

echo.
echo ============================================
echo   BUILD COMPLETE
echo ============================================
echo.
echo If you see "requires Java 11" error:
echo   → You MUST install JDK 17 first!
echo   → Download from: https://adoptium.net/
echo   → Or run: fix-jdk.ps1 as Administrator
echo.
pause
