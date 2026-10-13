param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][ValidatePattern('^[A-Za-z0-9_-]+$')][string]$Run,
    [ValidateSet('candidate', 'baseline')][string]$Engine = 'candidate',
    [string]$ApkRoot,
    [ValidateSet('java', 'native', 'gpu')][string]$Raster,
    [ValidateSet('on', 'off')][string]$LightingCache,
    [ValidateSet('on', 'off')][string]$GpuResident,
    [ValidateSet('on', 'off')][string]$GpuBatch,
    [ValidateSet('on', 'off')][string]$GpuTextureCache,
    [ValidateSet('on', 'off')][string]$GpuGeometry,
    [ValidateSet('on', 'off')][string]$GpuDiscardDepth,
    [ValidateRange(30,600)][int]$TestTimeoutSeconds = 300,
    [string]$Adb = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$package = "io.github.eriark.abyssme.m3g$Engine.debug"
$build = if ($ApkRoot) { (Resolve-Path $ApkRoot).Path } else { Join-Path $root "app/build/m3g-$Engine/outputs/apk" }
$apk = Join-Path $build 'fdroid/debug/AbyssME-0.1.0-beta.2-debug.apk'
$test = Join-Path $build 'androidTest/fdroid/debug/app-fdroid-debug-androidTest.apk'
$out = Join-Path $PSScriptRoot "build/benchmarks/$Run"
if (Test-Path -LiteralPath $out) { throw 'Use a new run label; evidence is never overwritten' }
foreach ($artifact in @(@{path=$apk; id=$package}, @{path=$test; id="$package.test"})) {
    $metadata = Get-Content -Raw (Join-Path (Split-Path $artifact.path) 'output-metadata.json') | ConvertFrom-Json
    if ($metadata.applicationId -ne $artifact.id) { throw 'Not an isolated research package' }
}
function Invoke-Adb([string[]]$Arguments) {
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $Adb
    $info.Arguments = ((@('-s', $Serial) + $Arguments) | ForEach-Object {
        if ($_ -match '"') { throw 'Unexpected quote in adb argument' }
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
        if (-not $process.WaitForExit($TestTimeoutSeconds * 1000)) {
            $process.Kill(); $process.WaitForExit()
            throw 'ADB timeout; no benchmark pass can be inferred'
        }
        if ($process.ExitCode -ne 0) { throw "ADB failed: $($stderr.Result) $($stdout.Result)" }
        return $stdout.Result
    } finally { $process.Dispose() }
}
New-Item -ItemType Directory -Path $out | Out-Null
$apkHash = (Get-FileHash $apk).Hash
$testHash = (Get-FileHash $test).Hash
Invoke-Adb @('install', '-r', $apk) | Out-Null
Invoke-Adb @('install', '-r', $test) | Out-Null
if ((Get-FileHash $apk).Hash -ne $apkHash -or (Get-FileHash $test).Hash -ne $testHash) {
    throw 'Artifacts changed during installation; use an immutable ApkRoot snapshot'
}
$arguments = @('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
    'javax.microedition.shell.M3gBenchmark', '-e', 'benchRun', $Run)
if ($Raster) { $arguments += @('-e', 'benchRaster', $Raster) }
if ($LightingCache) { $arguments += @('-e', 'benchLightingCache', $LightingCache) }
if ($GpuResident) { $arguments += @('-e', 'benchGpuResident', $GpuResident) }
if ($GpuBatch) { $arguments += @('-e', 'benchGpuBatch', $GpuBatch) }
if ($GpuTextureCache) { $arguments += @('-e', 'benchGpuTextureCache', $GpuTextureCache) }
if ($GpuGeometry) { $arguments += @('-e', 'benchGpuGeometry', $GpuGeometry) }
if ($GpuDiscardDepth) { $arguments += @('-e', 'benchGpuDiscardDepth', $GpuDiscardDepth) }
$arguments += "$package.test/androidx.test.runner.AndroidJUnitRunner"
$result = Invoke-Adb $arguments
$result | Set-Content -Encoding UTF8 (Join-Path $out 'tests.txt')
if ($result -notmatch 'OK \(1 test\)' -or $result -match 'FAILURES|INSTRUMENTATION_FAILED') {
    throw "Benchmark failed; see $out"
}
Invoke-Adb @('pull', "/sdcard/Android/data/$package/files/m3g-benchmark/$Run/.", $out) | Out-Null
$report = Get-Content -Raw (Join-Path $out 'report.json') | ConvertFrom-Json
$expectedCases = if ($report.PSObject.Properties.Name -contains 'gpuTextureCache') { 30 } else { 28 }
if ($report.package -ne $package -or $report.cases.Count -ne $expectedCases) { throw 'Unexpected benchmark report' }
[pscustomobject]@{
    engine = $Engine
    apkSha256 = $apkHash
    testApkSha256 = $testHash
    debuggable = $report.debuggable
    sampledUtc = [DateTime]::UtcNow.ToString('o')
} | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $out 'artifact.json')
$report.cases | ForEach-Object {
    [pscustomobject]@{
        scene = $_.name
        wallMs = if ($_.skipped) { $null } else { [math]::Round(($_.wallNs | Measure-Object -Average).Average / $_.framesPerBatch / 1e6, 3) }
        cpuMs = if ($_.skipped) { $null } else { [math]::Round(($_.threadCpuNs | Measure-Object -Average).Average / $_.framesPerBatch / 1e6, 3) }
        skipped = $_.skipped
    }
} | Format-Table
Write-Output "Evidence: $out; do not interpret synthetic frame cost as game FPS."
