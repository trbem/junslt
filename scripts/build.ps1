param([switch]$SkipTests)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$workspaceRoot = Split-Path -Parent $projectRoot
$javaRoot = Join-Path $workspaceRoot '.tools\java'
$jdk = if (Test-Path -LiteralPath $javaRoot) {
    Get-ChildItem -LiteralPath $javaRoot -Directory | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\java.exe') } | Select-Object -First 1
}
if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
$sdk = Join-Path $workspaceRoot '.tools\android-sdk'
if (Test-Path -LiteralPath $sdk) { $env:ANDROID_HOME = $sdk }
if (-not $env:JAVA_HOME -or -not $env:ANDROID_HOME) { throw '请先配置 JAVA_HOME 和 ANDROID_HOME' }
$env:GRADLE_USER_HOME = Join-Path $workspaceRoot '.tools\gradle-cache'
Push-Location -LiteralPath $projectRoot
try {
    if ($SkipTests) { & .\gradlew.bat assembleDebug --console=plain }
    else { & .\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain }
    if ($LASTEXITCODE -ne 0) { throw 'Gradle 构建／验证失败' }
} finally { Pop-Location }
