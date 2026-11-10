#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMPDIR=$(mktemp -d)
trap 'rm -rf "$TMPDIR"' EXIT
javac -d "$TMPDIR" \
  app/src/main/java/dev/zsk/wechatalert/PlaybackGate.java \
  tests/PlaybackGateTest.java
java -cp "$TMPDIR" dev.zsk.wechatalert.PlaybackGateTest
