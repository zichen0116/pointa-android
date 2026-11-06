[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
Push-Location $repo
try {
    & ./gradlew.bat :protocol:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console plain
    if ($LASTEXITCODE -ne 0) { throw 'Android build or validation failed.' }
    New-Item -ItemType Directory -Force -Path artifacts | Out-Null
    $metadata = Get-Content app/build/outputs/apk/debug/output-metadata.json -Raw | ConvertFrom-Json
    $version = $metadata.elements[0].versionName
    $artifact = "artifacts/Pointa-$version-debug.apk"
    Copy-Item -LiteralPath app/build/outputs/apk/debug/app-debug.apk -Destination $artifact
    Get-FileHash -LiteralPath $artifact -Algorithm SHA256 | Format-List
} finally { Pop-Location }
