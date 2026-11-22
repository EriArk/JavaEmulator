#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Assemble only the compiled sources, from the pinned revisions.
set -eu
potator=${1:?Pass the Potator checkout}
vremu=${2:?Pass the vrEmu6502 checkout}
stage=${3:?Pass an empty destination directory}
here=$(cd "$(dirname "$0")" && pwd)
set -o pipefail
test -z "$(ls -A "$stage")"
git -c safe.directory='*' -C "$potator" archive 227c5f6f3ce74d32e9002ce24c1420288559a860 \
    common/{controls,gpu,memorymap,sound,timer,watara}.c \
    common/{controls,gpu,memorymap,sound,supervision,timer,types}.h platform/libretro | tar -x -C "$stage"
git -c safe.directory='*' -C "$vremu" archive aae98cb14386d832cb7357c99626520b6590bc24 \
    src/vrEmu6502.{c,h} | tar -x -C "$stage"
patch --batch --fuzz=0 -s -d "$stage" -p1 < "$here/native/potator-cpu.patch"
patch --batch --fuzz=0 -s -d "$stage" -p1 < "$here/native/vremu-interrupt.patch"
