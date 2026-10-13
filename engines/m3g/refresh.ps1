param([switch]$Check, [switch]$Offline)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Push-Location $root
try {
    $arguments = @('-Pm3gExperiment=candidate', ':app:adaptM3gHost', ':app:prepareToneHost', '--console=plain')
    if ($Offline) { $arguments += '--offline' }
    & ./gradlew.bat @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Candidate preparation failed' }
    $sources = [ordered]@{
        'app/build/m3g-candidate/generated/m3g/java' = 'java'
        'experiments/m3g-host/java' = 'java'
        'experiments/m3g-host/tone-java' = 'java'
        'app/build/m3g-candidate/generated/tone-host' = 'java'
        'app/build/m3g-candidate/generated/m3g/assets' = 'assets'
    }
    $entries = @()
    foreach ($source in $sources.Keys) {
        $base = (Resolve-Path $source).Path
        foreach ($file in Get-ChildItem -LiteralPath $base -Recurse -File) {
            $relative = $sources[$source] + '/' + $file.FullName.Substring($base.Length + 1).Replace('\', '/')
            $entries += @{ source = $file.FullName; relative = $relative }
        }
    }
    foreach ($name in @('raster.cpp', 'bitmap.cpp')) {
        $entries += @{source = Join-Path $root "experiments/m3g-host/native/$name"; relative = "native/$name"}
    }
    $expected = @{}
    foreach ($entry in $entries) {
        if ($expected.ContainsKey($entry.relative)) { throw "Duplicate snapshot path: $($entry.relative)" }
        $content = [IO.File]::ReadAllText($entry.source).Replace("`r`n", "`n")
        if ($content -match 'org\.abyssme\.research|RenderStateTrace|researchTargetStart') {
            throw "Research hooks in runtime snapshot: $($entry.relative)"
        }
        $destination = Join-Path $PSScriptRoot $entry.relative
        if ($Check) {
            if (!(Test-Path -LiteralPath $destination) -or
                [IO.File]::ReadAllText($destination).Replace("`r`n", "`n") -cne $content) {
                throw "Production snapshot differs: $($entry.relative)"
            }
        } else {
            [IO.Directory]::CreateDirectory((Split-Path $destination)) | Out-Null
            [IO.File]::WriteAllText($destination, $content, [Text.UTF8Encoding]::new($false))
        }
        $expected[$entry.relative] = $true
    }
    foreach ($directory in @('java', 'assets')) {
        foreach ($file in Get-ChildItem (Join-Path $PSScriptRoot $directory) -Recurse -File) {
            $relative = $file.FullName.Substring($PSScriptRoot.Length + 1).Replace('\', '/')
            if (!$expected.ContainsKey($relative)) { throw "Unexpected snapshot file: $relative" }
        }
    }
    Write-Output "Production snapshot: $($entries.Count) files match the unmeasured candidate."
} finally { Pop-Location }
