param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [ValidateSet('candidate', 'baseline')][string]$Engine = 'candidate',
    [Parameter(Mandatory = $true)][ValidatePattern('^[a-zA-Z0-9_-]+$')][string]$Run,
    [ValidateRange(5,600)][int]$Seconds = 90,
    [string]$Adb = "$env:ANDROID_HOME/platform-tools/adb.exe"
)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/frame-metrics.ps1"
$package = "io.github.eriark.abyssme.m3g$Engine.debug"
$output = Join-Path $PSScriptRoot "build/measurements/$Run-$Engine"
if (Test-Path $output) { throw 'Run already exists; choose a new label to preserve evidence' }
New-Item -ItemType Directory $output -Force | Out-Null

function Adb([string[]]$Arguments) {
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $Adb
    $info.Arguments = ((@('-s', $Serial) + $Arguments) | ForEach-Object {
        if ($_ -match '"') { throw 'Unexpected double quote' }
        '"' + $_ + '"'
    }) -join ' '
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $process = [Diagnostics.Process]::Start($info)
    try {
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit(15000)) {
            $process.Kill()
            $process.WaitForExit()
            throw 'ADB measurement timeout'
        }
        if ($process.ExitCode -ne 0) { throw $stderr.Result }
        $stdout.Result.TrimEnd()
    } finally { $process.Dispose() }
}
function Assert-GameForeground {
    $activity = Adb @('shell', 'dumpsys', 'activity', 'activities')
    if ($activity -notmatch ('(?:mResumedActivity:|topResumedActivity=|ResumedActivity:).*' + [regex]::Escape($package) + '/javax.microedition.shell.MicroActivity')) { throw 'Game activity not resumed' }
}
function Capture([string]$Name) {
    Assert-GameForeground
    Adb @('shell', "screencap -p /sdcard/Android/data/$package/files/m3g-measure.png") | Out-Null
    Adb @('pull', "/sdcard/Android/data/$package/files/m3g-measure.png", "$output/$Name.png") | Out-Null
}
function PresentTimes {
    $dump = Adb @('shell', "dumpsys SurfaceFlinger --latency '$layer'")
    $dump | Add-Content -Encoding UTF8 "$output/surface.txt"
    @(Read-PresentTimes $dump)
}
function Memory {
    $dump = Adb @('shell', 'dumpsys', 'meminfo', $processId)
    $dump | Add-Content -Encoding UTF8 "$output/memory.txt"
    if ($dump -notmatch 'TOTAL PSS:\s+(\d+)') { throw 'Missing process PSS' }
    $pss = [int]$Matches[1]
    $memory.Add([pscustomobject]@{seconds=$watch.Elapsed.TotalSeconds; pssKiB=$pss})
}

$processId = (Adb @('shell', 'pidof', $package)).Trim()
if ($processId -notmatch '^\d+$') { throw 'Start exactly one research app process first' }
$layers = @(Read-GameSurface (Adb @('shell', 'dumpsys', 'SurfaceFlinger', '--list')) $package)
if ($layers.Count -ne 1) { throw 'Expected one active research game SurfaceView' }
$layer = $layers[0]
if ($layer -match "'") { throw 'Unsafe layer name' }
Adb @('shell', 'dumpsys', 'battery') | Set-Content -Encoding UTF8 "$output/battery-start.txt"
$apkPath = (Adb @('shell', 'pm', 'path', $package)) -replace '^package:', ''
if ($apkPath -notmatch '^/data/app/[^\r\n]+/base.apk$') { throw 'Unexpected APK path' }
$apkHash = ((Adb @('shell', 'sha256sum', $apkPath)) -split '\s+')[0]
Capture 'start'
$old = @(PresentTimes)
if ($old.Count -lt 2) { throw 'Surface has no usable presentation history' }
$cutoff = ($old | Measure-Object -Maximum).Maximum
$frames = New-Object 'System.Collections.Generic.HashSet[long]'
$memory = New-Object 'System.Collections.Generic.List[object]'
$watch = [Diagnostics.Stopwatch]::StartNew()
$startMs = [long](Adb @('shell', 'date', '+%s')) * 1000 + 1000
$lastMemory = -30
$lastFresh = 0.0
$previousPoll = 0.0
$maxPollGap = 0.0
$failure = $null
try {
    while ($watch.Elapsed.TotalSeconds -lt $Seconds) {
        if ((Adb @('shell', 'pidof', $package)).Trim() -ne $processId) { throw 'Game process changed/exited' }
        $now = $watch.Elapsed.TotalSeconds
        $maxPollGap = [Math]::Max($maxPollGap, $now - $previousPoll)
        $previousPoll = $now
        $fresh = 0
        foreach ($time in @(PresentTimes)) {
            if ($time -gt $cutoff -and $frames.Add($time)) { $fresh++ }
        }
        if ($fresh -gt 0) { $lastFresh = $watch.Elapsed.TotalSeconds }
        if ($watch.Elapsed.TotalSeconds - $lastFresh -gt 5) { throw 'No new presented frame for five seconds' }
        if ($now - $lastMemory -ge 30) {
            Assert-GameForeground
            Memory
            $lastMemory = $now
        }
        Start-Sleep -Milliseconds 800
    }
    Memory
} catch { $failure = $_.Exception.Message }
$watch.Stop()
$cadence = @()
try {
    $endMs = [long](Adb @('shell', 'date', '+%s')) * 1000
    $cadenceRaw = Adb @('shell', 'run-as', $package, 'cat', 'cache/m3g-cadence.jsonl')
    $cadenceRaw | Set-Content -Encoding UTF8 "$output/cadence-raw.jsonl"
    $cadence = @($cadenceRaw -split '\r?\n' | Where-Object { $_.StartsWith('{') } | ForEach-Object {
        $row = $_ | ConvertFrom-Json
        if ($row.pid -eq [int]$processId -and $row.startMs -ge $startMs -and $row.endMs -le $endMs) { $row }
    })
} catch { if (-not $failure) { $failure = "Cadence collection failed: $($_.Exception.Message)" } }
ConvertTo-Json -InputObject $cadence | Set-Content -Encoding UTF8 "$output/cadence.json"
if ($cadence.Count -eq 0 -and -not $failure) { $failure = 'No host cadence windows; build with -Pm3gMeasure=true' }
if (@($cadence | Where-Object { $_.overflow -gt 0 }).Count -gt 0 -and -not $failure) { $failure = 'Cadence sample overflow' }
$times = @($frames | Sort-Object)
$times | Set-Content -Encoding UTF8 "$output/present-ns.txt"
$memory | ConvertTo-Json | Set-Content -Encoding UTF8 "$output/memory.json"
$metrics = if ($times.Count -ge 2) { Get-FrameMetrics $times } else { $null }
# A long polling gap can overwrite the 128-record history at a 60 Hz surface.
if ($maxPollGap -ge 2 -and -not $failure) { $failure = 'Polling gap may have overwritten frame history' }
try { Capture 'end' } catch { if (-not $failure) { $failure = $_.Exception.Message } }
[pscustomobject]@{
    engine=$Engine; run=$Run; apkSha256=$apkHash; processId=$processId
    layer=$layer; elapsedSeconds=$watch.Elapsed.TotalSeconds; maxPollGapSeconds=$maxPollGap
    error=$failure; metrics=$metrics
    cadenceWindows=$cadence.Count
    interpretation='Surface presentation intervals, not game logic FPS or isolated renderer cost. Inspect captures.'
} | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 "$output/summary.json"
Adb @('shell', 'dumpsys', 'battery') | Set-Content -Encoding UTF8 "$output/battery-end.txt"
if ($failure) { throw $failure }
$metrics
"Evidence: $output"
