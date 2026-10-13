param([switch]$Offline)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $root
try {
    $args = @(':app:releaseDependencyInventory', '--console=plain')
    if ($Offline) { $args += '--offline' }
    & ./gradlew.bat @args
    if ($LASTEXITCODE -ne 0) { throw 'Dependency inventory failed' }
    $lock = Get-Content tools/release-source-lock.json -Raw | ConvertFrom-Json
    $output = Join-Path $root 'build/release-sources'
    foreach ($item in $lock.archives) {
        $path = Join-Path $output ('upstream/' + $item.file)
        [IO.Directory]::CreateDirectory((Split-Path $path)) | Out-Null
        if (!(Test-Path $path)) {
            if ($Offline) { throw "Source archive missing: $($item.file)" }
            & curl.exe -sS -L --fail --connect-timeout 20 --max-time 300 -o ($path + '.part') $item.url
            if ($LASTEXITCODE -ne 0) { throw 'Source download failed' }
            if ((Get-FileHash ($path + '.part')).Hash -ne $item.sha256) { throw 'Source archive hash mismatch' }
            Move-Item -LiteralPath ($path + '.part') -Destination $path
        }
        if ((Get-FileHash $path).Hash -ne $item.sha256) { throw "Source archive hash mismatch: $($item.file)" }
    }
    $rows = Get-Content (Join-Path $output 'dependencies.json') -Raw | ConvertFrom-Json
    foreach ($coordinate in $lock.mavenFallback) {
        $parts = $coordinate.Split(':')
        $name = "$($parts[1])-$($parts[2])-sources.jar"
        $relative = 'maven-fallback/' + $name
        $path = Join-Path $output $relative
        [IO.Directory]::CreateDirectory((Split-Path $path)) | Out-Null
        if (!(Test-Path $path)) {
            if ($Offline) { throw "Source JAR missing: $coordinate" }
            $url = 'https://repo.maven.apache.org/maven2/' + $parts[0].Replace('.', '/') + "/$($parts[1])/$($parts[2])/$name"
            & curl.exe -sS -L --fail --connect-timeout 20 --max-time 60 -o $path $url
            if ($LASTEXITCODE -ne 0) { throw "Source JAR download failed: $coordinate" }
        }
        $row = $rows | Where-Object coordinate -eq $coordinate
        $row.sources = @(@{file=$relative; sha256=(Get-FileHash $path).Hash.ToLowerInvariant()})
    }
    $substitutes = @{
        'com.github.nikita36078:mobile-ffmpeg:v4.3.2-compact' = 'mobile-ffmpeg-963b4eea.zip'
        'com.github.nikita36078:filepicker:4.3.0' = 'filepicker-dc26b828.zip'
    }
    foreach ($row in $rows) {
        if ($substitutes.ContainsKey($row.coordinate)) {
            $file = 'upstream/' + $substitutes[$row.coordinate]
            $row.sources = @(@{file=$file; sha256=(Get-FileHash (Join-Path $output $file)).Hash.ToLowerInvariant()})
        }
        if (!$row.sources.file -and $row.coordinate -ne $lock.emptyArtifact) { throw "Unresolved source: $($row.coordinate)" }
    }
    [IO.File]::WriteAllText((Join-Path $output 'dependencies.json'), ($rows | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
    Copy-Item tools/release-source-lock.json (Join-Path $output 'source-lock.json')
    Copy-Item tools/DEPENDENCY_SOURCES.md (Join-Path $output 'README.md')
    Write-Output 'Dependency sources resolved; review component provenance before publication.'
} finally { Pop-Location }
