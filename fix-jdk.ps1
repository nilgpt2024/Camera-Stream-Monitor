# Fix Android Studio JDK - Create missing jvm.cfg
# Run this script with Administrator privileges

$jvmCfgPath = "C:\Program Files\Android\Android Studio\jbr\lib\jvm.cfg"

if (Test-Path $jvmCfgPath) {
    Write-Host "jvm.cfg already exists!" -ForegroundColor Green
    exit 0
}

Write-Host "Creating jvm.cfg at: $jvmCfgPath" -ForegroundColor Yellow

$jvmContent = @"
-client KNOWN
-ignore-server KNOWN
-hotspot ALIASED_TO -client
-minimal KNOWN
-noverify IGNORE
-debug OPT
-verbosegc OPT
-noClassGC OPT
-verbose:class OPT
-noverify OPT
-verify:remote OPT
-dumpstats OPT
-loggc OPT
-opt:file OPT
-XX:+AggressiveOpts OPT
-opt:level=.* OPT
-jar OPT
-@file ARGUMENTS
-exit ARGUMENTS
-XshowSettings ARGUMENTS
-Xdiag ARGUMENTS
-hlp ARGUMENTS
-help ARGUMENTS
-? ARGUMENTS
-version ARGUMENTS
-fullversion ARGUMENTS
-showversion ARGUMENTS
-printversion ARGUMENTS
-diagnostic ARGUMENTS
"@

try {
    # Try to create the file
    [System.IO.File]::WriteAllText($jvmCfgPath, $jvmContent)
    
    if (Test-Path $jvmCfgPath) {
        Write-Host "SUCCESS: jvm.cfg created!" -ForegroundColor Green
        
        # Verify it works
        & "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" -version 2>&1 | Select-Object -First 1 | ForEach-Object {
            Write-Host "Java version: $_" -ForegroundColor Cyan
        }
        
        Write-Host "`nNow you can run: gradlew.bat assembleDebug" -ForegroundColor White
    } else {
        throw "File not created"
    }
} catch {
    Write-Host "`nERROR: Could not create jvm.cfg" -ForegroundColor Red
    Write-Host "Reason: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "`nAlternative solutions:" -ForegroundColor Yellow
    Write-Host "1. Run PowerShell as Administrator and try again" -ForegroundColor Gray
    Write-Host "2. Download portable JDK 17 to project folder" -ForegroundColor Gray
    Write-Host "   URL: https://adoptium.net/" -ForegroundColor Gray
}
