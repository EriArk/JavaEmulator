param([Parameter(Mandatory=$true)][string]$HostClasses, [string]$LegacyAsmJar)
$ErrorActionPreference = 'Stop'
$out = Join-Path $PSScriptRoot '../build/upgrade-probe'
New-Item -ItemType Directory -Force "$out/classes" | Out-Null
& "$env:JAVA_HOME/bin/javac.exe" --release 8 -classpath $HostClasses -d "$out/classes" "$PSScriptRoot/UpgradeProbe.java"
if ($LASTEXITCODE -ne 0) { throw 'Probe compilation failed' }
if ($LegacyAsmJar) {
    & "$env:JAVA_HOME/bin/java.exe" -cp $LegacyAsmJar "$PSScriptRoot/LegacyClass.java" "$out/classes"
    if ($LASTEXITCODE -ne 0) { throw 'Legacy fixture retargeting failed' }
}
& "$env:JAVA_HOME/bin/jar.exe" cfm "$out/UpgradeProbe.jar" "$PSScriptRoot/MANIFEST.MF" -C "$out/classes" .
if ($LASTEXITCODE -ne 0) { throw 'Probe archive failed' }
Get-FileHash "$out/UpgradeProbe.jar"
