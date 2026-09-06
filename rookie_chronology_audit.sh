#!/usr/bin/env bash
# Inventory season-cutoff coverage; does not certify historical information availability.
# Usage: ./rookie_chronology_audit.sh [output-directory]
# Examples:
#   ./rookie_chronology_audit.sh
#   ./rookie_chronology_audit.sh reports/fuad/chronology-experiment
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"
CLASSPATH_FILE="target/classpath.txt"
if [[ ! -f "$CLASSPATH_FILE" || "pom.xml" -nt "$CLASSPATH_FILE" ]]; then
    ./mvnw -q compile dependency:build-classpath -Dmdep.outputFile="$CLASSPATH_FILE"
else
    ./mvnw -q compile
fi
java -cp "target/classes:$(cat "$CLASSPATH_FILE")" ff.projection.fuad.RookieChronologyAudit "$@"
