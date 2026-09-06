#!/usr/bin/env bash
# Append-only local 2026 QB event forecast capture; draft-time eligibility is not certified.
# Usage: ./rookie_prospective_capture.sh [new-output-directory] | --verify capture-directory
# Examples:
#   ./rookie_prospective_capture.sh
#   ./rookie_prospective_capture.sh reports/fuad/captures/my-new-capture
#   ./rookie_prospective_capture.sh --verify reports/fuad/captures/my-new-capture
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"
./mvnw -q compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "target/classes:$(cat target/classpath.txt)" ff.projection.fuad.RookieProspectiveCapture "$@"
