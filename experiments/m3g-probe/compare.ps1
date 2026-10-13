param([Parameter(Mandatory = $true)][string]$Serial)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$safeSerial = $Serial -replace '[^a-zA-Z0-9_-]', '_'
$candidate = Join-Path $PSScriptRoot "probe/build/evidence/$safeSerial"
$baseline = Join-Path $PSScriptRoot "baseline/build/evidence/$safeSerial"
$candidateReport = Get-Content -Raw -LiteralPath (Join-Path $candidate 'report.json') | ConvertFrom-Json
$baselineReport = Get-Content -Raw -LiteralPath (Join-Path $baseline 'report.json') | ConvertFrom-Json
if ($candidateReport.suiteVersion -ne 2 -or $baselineReport.suiteVersion -ne 2) { throw 'Suite mismatch' }
if ($candidateReport.device -ne $baselineReport.device -or $candidateReport.abi -ne $baselineReport.abi) {
    throw 'Compare the same device/ABI'
}
$rows = @()
foreach ($name in $baselineReport.frames) {
    if ($name -notmatch '^[a-z_]+$') { throw 'Invalid frame name' }
    if ($candidateReport.frames -notcontains $name) {
        $rows += [pscustomobject]@{ frame = $name; missingCandidate = $true }
        continue
    }
    $a = [Drawing.Bitmap]::FromFile((Join-Path $candidate "$name.png"))
    $b = [Drawing.Bitmap]::FromFile((Join-Path $baseline "$name.png"))
    try {
        if ($a.Width -ne $b.Width -or $a.Height -ne $b.Height) { throw 'Frame dimensions differ' }
        $sum = 0L; $different = 0; $intersection = 0; $union = 0
        for ($y = 0; $y -lt $a.Height; $y++) {
            for ($x = 0; $x -lt $a.Width; $x++) {
                $p = $a.GetPixel($x, $y); $q = $b.GetPixel($x, $y)
                $errorRgb = [Math]::Abs([int]$p.R - $q.R) + [Math]::Abs([int]$p.G - $q.G) + [Math]::Abs([int]$p.B - $q.B)
                $sum += $errorRgb
                if ($errorRgb -gt 0) { $different++ }
                $pa = $p.R -gt 100 -and $p.G -gt 70 -and $p.B -lt 120
                $pb = $q.R -gt 100 -and $q.G -gt 70 -and $q.B -lt 120
                if ($pa -or $pb) { $union++ }
                if ($pa -and $pb) { $intersection++ }
            }
        }
        if ($union -eq 0) { throw "No geometry in either $name image" }
        $rows += [pscustomobject]@{
            frame = $name
            differentPixels = $different
            totalPixels = $a.Width * $a.Height
            meanAbsoluteRgbError = [Math]::Round($sum / (3.0 * $a.Width * $a.Height), 6)
            geometryIntersectionOverUnion = [Math]::Round($intersection / [double]$union, 6)
        }
    } finally { $a.Dispose(); $b.Dispose() }
}
$result = [pscustomobject]@{
    note = 'Descriptive pixel comparison, not a conformance or performance pass/fail threshold.'
    device = $candidateReport.device
    abi = $candidateReport.abi
    candidateApk = (Get-Content -Raw (Join-Path $candidate 'apk-hash.json') | ConvertFrom-Json).Hash
    baselineApk = (Get-Content -Raw (Join-Path $baseline 'apk-hash.json') | ConvertFrom-Json).Hash
    frames = $rows
}
$result | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 (Join-Path $candidate 'comparison.json')
$rows | Format-Table -AutoSize
