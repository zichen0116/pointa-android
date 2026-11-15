[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$InteropHost,
    [string]$DotNet = 'dotnet',
    [string]$Adb = 'adb',
    [switch]$AppFlow
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
if ($Serial -notmatch '^emulator-\d+$') { throw 'This isolated loopback test requires an Android emulator serial.' }
$taskDotNet = (Get-Command $DotNet -CommandType Application -ErrorAction Stop).Source
$taskAdb = (Get-Command $Adb -CommandType Application -ErrorAction Stop).Source
$taskHostPath = (Resolve-Path -LiteralPath $InteropHost).Path
$portProbe = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$portProbe.Start(); $port = $portProbe.LocalEndpoint.Port; $portProbe.Stop()
$info = [Diagnostics.ProcessStartInfo]::new($taskDotNet)
$info.UseShellExecute = $false
$info.CreateNoWindow = $true
$info.RedirectStandardInput = $true
$info.RedirectStandardOutput = $true
$info.RedirectStandardError = $true
$info.ArgumentList.Add($taskHostPath)
$info.ArgumentList.Add('--port')
$info.ArgumentList.Add([string]$port)
$process = [Diagnostics.Process]::Start($info)
Push-Location $repo
try {
    $fixture = $null
    for ($i = 0; $i -lt 10; $i++) {
        $line = $process.StandardOutput.ReadLineAsync()
        if (-not $line.Wait(15000)) { throw 'Windows test host did not become ready.' }
        $message = $line.Result | ConvertFrom-Json
        if ($message.event -eq 'ready') { $fixture = $message; break }
    }
    if ($null -eq $fixture) { throw 'No Windows test fixture received.' }
    # Continuous air-pointer events must not fill the host's redirected pipes and block its replies.
    $outputDrain = $process.StandardOutput.ReadToEndAsync()
    $errorDrain = $process.StandardError.ReadToEndAsync()
    & $taskAdb -s $Serial install -r app/build/outputs/apk/debug/app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'App installation failed.' }
    & $taskAdb -s $Serial install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    if ($LASTEXITCODE -ne 0) { throw 'Test installation failed.' }
    $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(($fixture | ConvertTo-Json -Compress))).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    New-Item -ItemType Directory -Force -Path artifacts | Out-Null
    if ($AppFlow) {
        $result = & $taskAdb -s $Serial shell am instrument -w -e class io.github.zichen0116.pointa.AppFlowTest -e pointa.appFixture $encoded io.github.zichen0116.pointa.test/androidx.test.runner.AndroidJUnitRunner
        $result | Set-Content -LiteralPath artifacts/app-flow.txt
    } else {
        $result = & $taskAdb -s $Serial shell am instrument -w -e notClass io.github.zichen0116.pointa.AppFlowTest,io.github.zichen0116.pointa.LiveLanProbeTest -e pointa.fixture $encoded io.github.zichen0116.pointa.test/androidx.test.runner.AndroidJUnitRunner
        $result | Set-Content -LiteralPath artifacts/device-smoke.txt
    }
    $result | Select-Object -Last 20
    if ($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'OK \(\d+ tests?\)' -or ($result -join "`n") -match 'FAILURES|INSTRUMENTATION_FAILED') { throw 'Device tests failed; inspect artifacts/device-smoke.txt.' }
} finally {
    if (-not $process.HasExited) {
        $process.StandardInput.WriteLine('{"command":"quit"}')
        $process.StandardInput.Flush()
        if (-not $process.WaitForExit(5000)) { $process.Kill($true); $process.WaitForExit() }
    }
    $process.Dispose()
    Pop-Location
}
