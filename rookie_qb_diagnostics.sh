#!/usr/bin/env bash
# Diagnose QB rookie forecast errors using class holdouts and fixed replacement sensitivities.
# Usage: ./rookie_qb_diagnostics.sh [output-directory]
# Examples:
#   ./rookie_qb_diagnostics.sh
#   ./rookie_qb_diagnostics.sh reports/fuad/qb-experiment
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"
CLASSPATH_FILE="target/classpath.txt"
if [[ ! -f "$CLASSPATH_FILE" || "pom.xml" -nt "$CLASSPATH_FILE" ]]; then
    ./mvnw -q compile dependency:build-classpath -Dmdep.outputFile="$CLASSPATH_FILE"
else
    ./mvnw -q compile
fi
java -cp "target/classes:$(cat "$CLASSPATH_FILE")" ff.projection.fuad.RookieQbDiagnostics "$@"
