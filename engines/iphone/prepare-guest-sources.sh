#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# The two GNU guest libraries in touchHLE v0.3.0 come from Apple's iPhoneOS 4.0 SDK.
set -eu
set -o pipefail
out=${1:?Pass an output directory for the guest sources}
mkdir -p "$out"
temp=$(mktemp -d "$out/.guest-source-XXXXXX")
trap 'rm -rf "$temp"' EXIT
git -C "$temp" init -q
pack() {
    local repo=$1 version=$2 pin=$3
    git -C "$temp" fetch -q --depth=1 "https://github.com/apple-oss-distributions/$repo.git" "$pin"
    git -C "$temp" archive --prefix="$version/" "$pin" | gzip -n > "$temp/$version.tar.gz"
}
pack libstdcxx libstdcxx-39 dac27f80215d2c496945244c2cc83fadca676335
pack gcc gcc-5664 e19d86db70127e160b1c32557e0bb80ebc272f92
(cd "$temp" && sha256sum libstdcxx-39.tar.gz gcc-5664.tar.gz > GNU-guest-sources.sha256)
mv "$temp"/{libstdcxx-39.tar.gz,gcc-5664.tar.gz,GNU-guest-sources.sha256} "$out/"
