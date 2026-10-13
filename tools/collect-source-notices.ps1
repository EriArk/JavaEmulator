param([string]$Sources = 'build/release-sources')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$out = Join-Path $PSScriptRoot '../app/src/main/assets/licenses/dependencies'
[IO.Directory]::CreateDirectory($out) | Out-Null
foreach ($file in Get-ChildItem $Sources -Recurse -Filter '*-sources.jar') {
    $notices = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $zip = [IO.Compression.ZipFile]::OpenRead($file.FullName)
    try {
        foreach ($entry in $zip.Entries) {
            if ($entry.Length -eq 0) { continue }
            $standalone = $entry.Name -match '^(LICENSE|NOTICE|COPYING)(\..*)?$'
            if (!$standalone -and $entry.Name -notmatch '\.(java|kt)$') { continue }
            $reader = [IO.StreamReader]::new($entry.Open())
            try { $text = $reader.ReadToEnd() } finally { $reader.Dispose() }
            if (!$standalone) {
                $text = [regex]::Match($text, '\A\s*(?:(?://[^\r\n]*(?:\r?\n|$))|(?:/\*[\s\S]*?\*/\s*))+').Value
                if ($text -notmatch '(?i)copyright|license|SPDX') { continue }
            }
            $null = $notices.Add($text.Trim())
        }
    } finally { $zip.Dispose() }
    if ($notices.Count) {
        $content = "Source artifact: $($file.Name)`n`n" + (($notices | Sort-Object) -join "`n`n-----`n`n") + "`n"
        [IO.File]::WriteAllText((Join-Path $out ($file.BaseName + '.txt')), $content, [Text.UTF8Encoding]::new($false))
    }
}
Write-Output "Collected source notices in $out. This supplements, not replaces, the component license review."
