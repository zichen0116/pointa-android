[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
Push-Location $repo
try {
    & ./gradlew.bat :protocol:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console plain
    if ($LASTEXITCODE -ne 0) { throw 'Android build or validation failed.' }
    New-Item -ItemType Directory -Force -Path artifacts | Out-Null
    Copy-Item -LiteralPath app/build/outputs/apk/debug/app-debug.apk -Destination artifacts/Pointa-0.1.0-debug.apk
    Get-FileHash artifacts/Pointa-0.1.0-debug.apk -Algorithm SHA256 | Format-List
} finally { Pop-Location }
