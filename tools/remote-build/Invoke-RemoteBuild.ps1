param(
    [ValidatePattern('^[A-Za-z0-9_.-]+$')][string]$RemoteHost = 'books-server',
    [string]$Revision = 'HEAD',
    [ValidateSet('unsigned', 'debug')][string]$Mode = 'unsigned'
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Push-Location $repo
try {
    $sha = & git rev-parse "$Revision^{commit}"
    if ($LASTEXITCODE -ne 0 -or $sha -notmatch '^[0-9a-f]{40}$') { throw 'Invalid source revision' }
    if (& git status --porcelain --untracked-files=normal) {
        throw 'Commit and push the intended source first; private/WIP files are never uploaded.'
    }
    & ssh $RemoteHost 'mkdir -p "$HOME/abyssme-build"'
    if ($LASTEXITCODE -ne 0) { throw 'SSH setup failed' }
    & scp (Join-Path $PSScriptRoot 'run.sh') "${RemoteHost}:abyssme-build/run.sh"
    if ($LASTEXITCODE -ne 0) { throw 'Build launcher transfer failed' }
    $local = Join-Path $repo 'build/remote-artifacts'
    New-Item -ItemType Directory -Force $local | Out-Null
    $log = Join-Path $local ("request-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')
    $ErrorActionPreference = 'Continue'
    try {
        & ssh $RemoteHost "bash abyssme-build/run.sh $sha $Mode" 2>&1 | ForEach-Object { $_.ToString() } | Tee-Object -FilePath $log
        $buildExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = 'Stop' }
    if ($buildExit -ne 0) { throw "Remote build failed; retained log: $log" }
    $line = Get-Content $log | Where-Object { $_ -match '^Artifacts: ' } | Select-Object -Last 1
    if (!$line -or $line -notmatch '/out/(\d{8}T\d{6}Z-[0-9a-f]{12}-(unsigned|debug))$') {
        throw 'Missing/invalid artifact directory in build response'
    }
    $run = $Matches[1]
    $destination = Join-Path $local $run
    if (Test-Path $destination) { throw 'Refusing to merge with a previous artifact download' }
    New-Item -ItemType Directory $destination | Out-Null
    & scp "${RemoteHost}:abyssme-build/out/$run/*" $destination
    if ($LASTEXITCODE -ne 0) { throw 'Artifact download failed; partial files retained' }
    $checksums = @(Get-Content (Join-Path $destination 'SHA256SUMS.txt'))
    if ($checksums.Count -ne 1) { throw 'Expected exactly one APK checksum' }
    $info = Get-Content (Join-Path $destination 'BUILD-INFO.json') -Raw | ConvertFrom-Json
    if ($info.revision -ne $sha -or $info.mode -ne $Mode) { throw 'Artifact provenance mismatch' }
    foreach ($entry in $checksums) {
        if ($entry -notmatch '^([0-9a-f]{64})  ([A-Za-z0-9_.-]+\.apk)$') { throw 'Invalid checksum manifest' }
        $expected = $Matches[1]
        if ($info.sha256 -ne $expected -or $info.apk -ne $Matches[2]) { throw 'Artifact manifest mismatch' }
        if ((Get-FileHash (Join-Path $destination $Matches[2])).Hash.ToLowerInvariant() -ne $expected) {
            throw 'Downloaded APK checksum mismatch'
        }
    }
    Write-Output "Verified artifacts: $destination"
    Write-Output 'Unsigned APKs require local signing; this command never publishes or installs them.'
} finally { Pop-Location }
