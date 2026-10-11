$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/frame-metrics.ps1"
$parsed = @(Read-PresentTimes "16666666`n1 0 2`n1 9223372036854775807 2`n5 1000000000 6`n5 1100000000 6")
if ($parsed.Count -ne 2 -or $parsed[0] -ne 1000000000) { throw 'Sentinel/column parsing failed' }
$result = Get-FrameMetrics @(1200000000,1000000000,1100000000,1100000000)
if ($result.frames -ne 3 -or $result.presentedFps -ne 10 -or $result.intervalP95Ms -ne 100) { throw 'Dedup/order metrics failed' }
$result = Get-FrameMetrics @(1000000000,1010000000,1210000000)
if ($result.intervalsAbove100Ms -ne 1 -or $result.maxIntervalMs -ne 200) { throw 'Long gap was hidden' }
$failed = $false
try { Get-FrameMetrics @(100,100) | Out-Null } catch { $failed = $true }
if (-not $failed) { throw 'Empty coverage was accepted' }
$layer = 'SurfaceView[example.test/javax.microedition.shell.MicroActivity](BLAST)#265'
if (@(Read-GameSurface "Background for $layer`n$layer" 'example.test').Count -ne 1) { throw 'Android 14 layer parsing failed' }
if ((Read-GameSurface "RequestedLayerState{$layer parentId=264}" 'example.test') -ne $layer) { throw 'Android 15 layer parsing failed' }
'6/6 frame-metric checks passed'
