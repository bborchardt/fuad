#!/usr/bin/env bash
# Nested rank-aware QB event frequencies; no production change.
# Allow about three minutes, depending on compilation and machine speed.
# Usage: ./rookie_rank_frequency.sh [output-directory]
# Examples:
#   ./rookie_rank_frequency.sh
#   ./rookie_rank_frequency.sh reports/fuad/rank-frequency-experiment
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"
CLASSPATH_FILE="target/classpath.txt"
if [[ ! -f "$CLASSPATH_FILE" || "pom.xml" -nt "$CLASSPATH_FILE" ]]; then
    ./mvnw -q compile dependency:build-classpath -Dmdep.outputFile="$CLASSPATH_FILE"
else
    ./mvnw -q compile
fi
java -cp "target/classes:$(cat "$CLASSPATH_FILE")" ff.projection.fuad.RookieRankFrequency "$@"
