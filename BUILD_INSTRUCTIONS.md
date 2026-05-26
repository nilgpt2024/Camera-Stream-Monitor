# Video Monitor Project - Build Instructions

## Quick Start (3 Methods)

### Method 1: Automatic (Recommended) ⭐
Double-click: `build.bat`
- Automatically detects JDK 17
- Shows build progress and results
- No manual configuration needed

### Method 2: Download Portable JDK
1. Double-click: `download-jdk17.bat`
   - Downloads OpenJDK 17 (~180 MB)
   - Extracts to `jdk17/` folder
2. Then run: `build.bat` or `gradlew.bat assembleDebug`

### Method 3: Fix Android Studio JDK (Requires Admin)
1. Right-click: `fix-jdk17.bat` → **Run as Administrator**
2. Creates missing jvm.cfg file
3. Then run: `build.bat` or `gradlew.bat assembleDebug`

---

## Problem Explanation

**Error**: `Android Gradle plugin requires Java 11 to run. You are currently using Java 1.8`

**Root Cause**: 
- Your system has Java 8 installed
- Android Gradle Plugin (AGP) requires Java 11+
- Android Studio includes JDK 17 but it's misconfigured

**Solution**: Use one of the methods above to provide JDK 17

---

## Advanced Options

### Clean Build
```powershell
.\build.ps1 -Clean
```

### Custom Task
```powershell
.\build.ps1 -Task "assembleRelease"
```

### Manual Configuration
Edit `gradle.properties` and uncomment one line:
```properties
# Option A: Portable JDK
org.gradle.java.home=..\\jdk17

# Option B: Android Studio JDK  
org.gradle.java.home=C:\\Program Files\\Android\\Android Studio\\jbr
```

---

## Troubleshooting

### Issue: "could not open jvm.cfg"
**Fix**: Run `fix-jdk17.bat` as Administrator, OR use Method 2

### Issue: Download fails
**Manual download**: 
```
https://download.java.net/java/GA/jdk17.0.9/8e217d225f7c4c439ba760db8a3eb0c40/11/GPL/openjdk-17.0.9_windows-x64_bin.zip
```
Extract to project folder as `jdk17/`

### Issue: Still using Java 8 after fix
**Check**: Ensure you're running `build.bat`, not directly running `gradlew.bat`

---

## File Descriptions

| File | Purpose |
|------|---------|
| `build.bat` | Main entry point - double-click this |
| `build.ps1` | Smart build script with auto-detection |
| `download-jdk17.bat` | Downloads portable JDK 17 |
| `fix-jdk17.bat` | Fixes Android Studio JDK (needs admin) |
| `build-with-jdk17.bat` | Alternative build script |

---

## System Requirements

- **JDK**: 17 (auto-detected by build script)
- **Gradle**: 8.0 (included in wrapper)
- **Android SDK**: API 34 (configured in build.gradle.kts)
- **Memory**: 2GB+ RAM recommended

---

## Success Indicators

✅ You should see:
```
[OK] Found working JDK at: ...\jdk17
     Version: openjdk version "17.0.9"...
     
[BUILD SUCCESSFUL!]
APK Location: app\build\outputs\apk\debug\
APK File: app-debug.apk (X.XX MB)
```

❌ If you see errors, check the troubleshooting section above.

---

**Last Updated**: 2026-04-22
**Project**: Video Monitoring System (Android)
