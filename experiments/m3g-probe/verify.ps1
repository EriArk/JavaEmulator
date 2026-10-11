param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [string]$Adb = "$env:ANDROID_HOME/platform-tools/adb.exe"
)
$ErrorActionPreference = 'Stop'
$package = 'io.github.eriark.m3gprobe'
$apk = Join-Path $PSScriptRoot 'probe/build/outputs/apk/debug/probe-debug.apk'
$safeSerial = $Serial -replace '[^a-zA-Z0-9_-]', '_'
$output = Join-Path $PSScriptRoot "probe/build/evidence/$safeSerial"
New-Item -ItemType Directory -Force -Path $output | Out-Null

function Invoke-Adb {
    param([string[]]$Arguments)
    $result = & $Adb -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $result" }
    return $result
}

Invoke-Adb @('install', '-r', $apk)
Invoke-Adb @('shell', 'am', 'force-stop', $package)
# Delete only this probe's previous result, never game or library data.
Invoke-Adb @('shell', 'run-as', $package, 'rm', '-f', 'files/report.json')
Invoke-Adb @('shell', 'am', 'start', '-W', '-n', "$package/.ProbeActivity")
$report = $null
for ($attempt = 0; $attempt -lt 60; $attempt++) {
    try {
        $raw = Invoke-Adb @('shell', 'run-as', $package, 'cat', 'files/report.json')
        $report = ($raw -join "`n") | ConvertFrom-Json
        break
    } catch { }
    Start-Sleep -Seconds 1
}
if ($null -eq $report) { throw 'No fresh probe report within 60 seconds' }
$raw | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $output 'report.json')
Get-FileHash -Algorithm SHA256 -LiteralPath $apk |
    Select-Object Algorithm, Hash | ConvertTo-Json |
    Set-Content -Encoding UTF8 -LiteralPath (Join-Path $output 'apk-hash.json')
$failed = @($report.tests | Where-Object { -not $_.passed })
if ($report.tests.Count -ne 8 -or $failed.Count -ne 0) {
    throw "Probe failed or incomplete: $($failed | ConvertTo-Json -Compress)"
}
Write-Output "PASS: 8/8 on $($report.device) ($($report.abi)); evidence: $output"
