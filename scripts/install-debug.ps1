$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"

Push-Location $projectRoot
try {
    gradle :app:assembleDebug
    & $adb install -r "app\build\outputs\apk\debug\app-debug.apk"
    & $adb shell am force-stop com.example.takemehome
    & $adb shell monkey -p com.example.takemehome 1
} finally {
    Pop-Location
}
