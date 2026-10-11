param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [switch]$Baseline,
    [string]$Adb = "$env:ANDROID_HOME/platform-tools/adb.exe"
)
$ErrorActionPreference = 'Stop'
$module = if ($Baseline) { 'baseline' } else { 'probe' }
$package = if ($Baseline) { 'io.github.eriark.m3gprobe.baseline' } else { 'io.github.eriark.m3gprobe' }
$expected = if ($Baseline) { 10 } else { 18 }
$apk = Join-Path $PSScriptRoot "$module/build/outputs/apk/debug/$module-debug.apk"
$safeSerial = $Serial -replace '[^a-zA-Z0-9_-]', '_'
$output = Join-Path $PSScriptRoot "$module/build/evidence/$safeSerial"
New-Item -ItemType Directory -Force -Path $output | Out-Null

function Invoke-Adb {
    param([string[]]$Arguments)
    $start = New-Object System.Diagnostics.ProcessStartInfo
    $start.FileName = $Adb
    $start.Arguments = ((@('-s', $Serial) + $Arguments) | ForEach-Object {
        if ($_ -match '"') { throw 'Unexpected quote in adb argument' }
        '"' + $_ + '"'
    }) -join ' '
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::Start($start)
    try {
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit(60000)) {
            $process.Kill(); $process.WaitForExit()
            throw "adb timed out: $($Arguments -join ' ')"
        }
        if ($process.ExitCode -ne 0) { throw "adb failed: $($stderr.Result) $($stdout.Result)" }
        return $stdout.Result.TrimEnd()
    } finally { $process.Dispose() }
}

Invoke-Adb @('install', '-r', $apk)
Invoke-Adb @('shell', 'am', 'force-stop', $package)
# Delete only this probe's previous result, never game or library data.
Invoke-Adb @('shell', 'run-as', $package, 'rm', '-f', 'files/report.json')
Invoke-Adb @('shell', 'am', 'start', '-W', '-n', "$package/io.github.eriark.m3gprobe.ProbeActivity")
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
foreach ($frame in $report.frames) {
    if ($frame -notmatch '^[a-z_]+$' -or $Serial -notmatch '^[a-zA-Z0-9_.:-]+$') {
        throw 'Unexpected capture name or serial'
    }
    $start = New-Object System.Diagnostics.ProcessStartInfo
    $start.FileName = $Adb
    $start.Arguments = "-s $Serial exec-out run-as $package cat files/$frame.png"
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::Start($start)
    $errors = $process.StandardError.ReadToEndAsync()
    $file = [IO.File]::Create((Join-Path $output "$frame.png"))
    try {
        $copy = $process.StandardOutput.BaseStream.CopyToAsync($file)
        if (-not $process.WaitForExit(60000)) {
            $process.Kill(); $process.WaitForExit()
            throw "Frame capture timed out: $frame"
        }
        [void]$copy.GetAwaiter().GetResult()
    } finally { $file.Dispose() }
    if ($process.ExitCode -ne 0) { throw "Frame capture failed: $($errors.Result)" }
    $process.Dispose()
}
Get-FileHash -Algorithm SHA256 -LiteralPath $apk |
    Select-Object Algorithm, Hash | ConvertTo-Json |
    Set-Content -Encoding UTF8 -LiteralPath (Join-Path $output 'apk-hash.json')
$failed = @($report.tests | Where-Object { -not $_.passed })
if ($report.suiteVersion -ne 2 -or $report.tests.Count -ne $expected -or $failed.Count -ne 0) {
    throw "Probe failed or incomplete: $($failed | ConvertTo-Json -Compress)"
}
Write-Output "PASS: $expected/$expected on $($report.device) ($($report.abi)); evidence: $output"
