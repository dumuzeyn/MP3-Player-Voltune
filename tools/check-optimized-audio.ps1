param([string]$Serial = "emulator-5554", [switch]$SkipBuild)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$adb = Join-Path $root ".android-sdk/platform-tools/adb.exe"
$package = "com.dumuzeyn.mp3player.benchmark"
$started = $false
Push-Location $root
try {
    if (!$SkipBuild) {
        & ./gradlew.bat :app:assembleBenchmark --no-problems-report --max-workers=2
        if ($LASTEXITCODE -ne 0) { throw "Optimized build failed" }
    }
    & $adb -s $Serial install -r app/build/outputs/apk/benchmark/app-benchmark.apk
    if ($LASTEXITCODE -ne 0) { throw "Benchmark install failed" }
    & $adb -s $Serial shell am force-stop $package
    & $adb -s $Serial logcat -c
    & $adb -s $Serial shell am start -W -n "$package/com.dumuzeyn.mp3player.OptimizedAudioSmokeActivity"
    if ($LASTEXITCODE -ne 0) { throw "Smoke activity did not start" }
    $started = $true
    $deadline = (Get-Date).AddMinutes(4)
    do {
        $logs = & $adb -s $Serial logcat -d -s VoltuneOptimizedSmoke:I AndroidRuntime:E
        if ($logs -match "FAIL: optimized|FATAL EXCEPTION") {
            $logs | Tee-Object -FilePath app/build/reports/optimized-audio-smoke.txt
            throw "Optimized audio smoke failed"
        }
        if ($logs -match "PASS: FFT") {
            $logs | Tee-Object -FilePath app/build/reports/optimized-audio-smoke.txt
            exit 0
        }
        Start-Sleep -Seconds 5
    } while ((Get-Date) -lt $deadline)
    $logs | Tee-Object -FilePath app/build/reports/optimized-audio-smoke.txt
    throw "Optimized audio smoke timed out"
} finally {
    if ($started) { & $adb -s $Serial shell am force-stop $package }
    Pop-Location
}
