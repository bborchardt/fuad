#!/usr/bin/env bash
# Outcome-season-truncated validation under fixed 2026 rules; not a certified historical replay.
# Usage: ./rookie_chronological_study.sh [output-directory]
# Examples:
#   ./rookie_chronological_study.sh
#   ./rookie_chronological_study.sh reports/fuad/chronology-experiment
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"
./mvnw -q compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "target/classes:$(cat target/classpath.txt)" ff.projection.fuad.RookieChronologicalStudy "$@"
