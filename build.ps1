$ErrorActionPreference = 'Stop'
$projectDir = $PSScriptRoot
$toolDir = Join-Path (Split-Path $projectDir -Parent) 'work/tools'
$jdkDir = Join-Path $toolDir 'jdk'
if (Test-Path -LiteralPath $jdkDir) {
    $localJdk = Get-ChildItem -LiteralPath $jdkDir -Directory | Select-Object -First 1
    if ($localJdk) { $env:JAVA_HOME = $localJdk.FullName }
}
if (Test-Path -LiteralPath (Join-Path $toolDir 'android-sdk')) {
    $env:ANDROID_HOME = Join-Path $toolDir 'android-sdk'
    $env:ANDROID_USER_HOME = Join-Path $toolDir 'android-user'
    $env:GRADLE_USER_HOME = Join-Path $toolDir 'gradle-cache'
}
Push-Location -LiteralPath $projectDir
try {
    & .\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Build failed with exit code $LASTEXITCODE" }
    Write-Output (Join-Path $projectDir 'app/build/outputs/apk/debug/app-debug.apk')
} finally { Pop-Location }
