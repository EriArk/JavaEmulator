param(
    [Parameter(Mandatory=$true)][string]$SourceFrame,
    [string]$Upstream = 'build/retro-filter-upstream'
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$upstreamPath = if ([IO.Path]::IsPathRooted($Upstream)) { $Upstream } else { Join-Path $repo $Upstream }
$revision = 'cba01a48b0974a1232bea68d999a7c21bc4c233e'
$actual = & git -C $upstreamPath rev-parse HEAD
if ($LASTEXITCODE -ne 0 -or $actual -ne $revision) {
    throw "Prepare a local libretro/glsl-shaders checkout at $revision before staging."
}
$source = Get-Item -LiteralPath $SourceFrame
if ($source.PSIsContainer) { throw 'SourceFrame must be a PNG file.' }
$destination = Join-Path $repo 'build/retro-review'
$paths = @(
    'stock.glsl',
    'eagle/shaders/supereagle.glsl',
    'xbr/shaders/xbr-lv2-noblend.glsl',
    'xbrz/shaders/xbrz-freescale.glsl',
    'cel/shaders/advcartoon.glsl',
    'crt/shaders/crt-easymode.glsl',
    'crt/shaders/crt-geom.glsl',
    'hqx/shader-files/hqx-pass1.glsl',
    'hqx/shader-files/hqx-pass2.glsl',
    'hqx/resources/hq2x.png',
    'hqx/resources/hq3x.png',
    'hqx/resources/hq4x.png'
)
$paths += 0..4 | ForEach-Object { "scalefx/shaders/scalefx-pass$_.glsl" }
& git -C $upstreamPath diff --quiet HEAD -- $paths
if ($LASTEXITCODE -ne 0) { throw 'The selected upstream files have local changes.' }
foreach ($path in $paths) {
    $target = Join-Path (Join-Path $destination 'shaders') $path
    New-Item -ItemType Directory -Path (Split-Path $target) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $upstreamPath $path) -Destination $target
}
Copy-Item -LiteralPath $source.FullName -Destination (Join-Path $destination 'source.png')
Write-Output "Staged 17 upstream files and one local frame in $destination. Nothing added to APK assets."
