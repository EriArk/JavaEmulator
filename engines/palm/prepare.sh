#!/usr/bin/env bash
set -eu
out=${1:-build/palm}
mkdir -p "$out/assets/palm" "$out/assets/licenses/palm"
zip="$out/cloudpilot-emu-embedded-2.3.0.zip"
if [[ ! -f "$zip" ]]; then
    curl -fL https://github.com/cloudpilot-emu/cloudpilot-emu/releases/download/2.3.0/cloudpilot-emu-embedded-2.3.0.zip -o "$zip"
fi
echo "32a3c245f105491ac6ed5619d004e240931b680b1e3a87c587c1b53e7e175bf7  $zip" | sha256sum -c -
unzip -oq "$zip" '*.wasm' '*.js' -d "$out/assets/palm"
curl -fsSL https://raw.githubusercontent.com/cloudpilot-emu/cloudpilot-emu/2.3.0/LICENSE -o "$out/assets/licenses/palm/LICENSE"
