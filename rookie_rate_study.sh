#!/usr/bin/env bash
# Conditional QB scoring-rate and rate/games dependence diagnostics; no production change.
# Allow about two minutes for the full run; compilation and machine speed affect runtime.
# Usage: ./rookie_rate_study.sh [output-directory]
# Examples:
#   ./rookie_rate_study.sh
#   ./rookie_rate_study.sh reports/fuad/rate-experiment
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"
CLASSPATH_FILE="target/classpath.txt"
if [[ ! -f "$CLASSPATH_FILE" || "pom.xml" -nt "$CLASSPATH_FILE" ]]; then
    ./mvnw -q compile dependency:build-classpath -Dmdep.outputFile="$CLASSPATH_FILE"
else
    ./mvnw -q compile
fi
java -cp "target/classes:$(cat "$CLASSPATH_FILE")" ff.projection.fuad.RookieRateStudy "$@"
