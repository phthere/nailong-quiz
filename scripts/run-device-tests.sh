#!/usr/bin/env bash
set -euo pipefail

# Keep native screenshots even when an assertion fails, before the emulator exits.
collect_proof() {
  mkdir -p device-proof
  for name in pdf-import-preview document-import-preview direct-import ui-home ui-home-actions ui-bank ui-stats ui-home-large ui-last-state; do
    adb pull "/data/local/tmp/nailong-$name.png" "device-proof/$name.png" || true
  done
}
trap collect_proof EXIT
./gradlew --no-daemon --max-workers=2 --stacktrace :app:connectedDebugAndroidTest
