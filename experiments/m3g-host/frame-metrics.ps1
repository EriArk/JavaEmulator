function Read-GameSurface([string]$Dump, [string]$Package) {
    foreach ($line in $Dump -split '\r?\n') {
        if ($line -match '^RequestedLayerState\{(.+?) parentId=') { $line = $Matches[1] }
        if ($line.StartsWith("SurfaceView[$Package/") -and $line -match '\(BLAST\)#\d+$') { $line }
    }
}

function Read-PresentTimes([string]$Dump) {
    foreach ($line in $Dump -split '\r?\n') {
        if ($line -match '^\s*\d+\s+(\d+)\s+\d+\s*$') {
            $time = [long]$Matches[1]
            if ($time -gt 0 -and $time -lt [long]::MaxValue) { $time }
        }
    }
}

function Get-FrameMetrics([long[]]$Times) {
    $ordered = @($Times | Sort-Object -Unique)
    if ($ordered.Count -lt 2) { throw 'Insufficient presented frames; no FPS result' }
    $intervals = @(for ($i = 1; $i -lt $ordered.Count; $i++) {
        ($ordered[$i] - $ordered[$i - 1]) / 1000000.0
    })
    $sorted = @($intervals | Sort-Object)
    $span = ($ordered[-1] - $ordered[0]) / 1000000000.0
    [pscustomobject]@{
        frames = $ordered.Count
        spanSeconds = $span
        presentedFps = ($ordered.Count - 1) / $span
        intervalP50Ms = $sorted[[Math]::Ceiling($sorted.Count * 0.50) - 1]
        intervalP95Ms = $sorted[[Math]::Ceiling($sorted.Count * 0.95) - 1]
        maxIntervalMs = $sorted[-1]
        intervalsAbove100Ms = @($intervals | Where-Object { $_ -gt 100 }).Count
    }
}
