#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
set -euo pipefail
cd "$(dirname "$0")/.."
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
javac -d "$classes" \
  engines/symbian/core/src/main/java/io/github/eriark/abyssme/symbian/PhoneTransferQueue.java \
  tools/tests/PhoneTransferQueueTest.java
java -cp "$classes" io.github.eriark.abyssme.symbian.PhoneTransferQueueTest
