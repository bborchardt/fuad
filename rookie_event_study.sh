#!/usr/bin/env bash
# Fixed QB annual VOR event probabilities; no production change.
# Allow about two minutes, depending on compilation and machine speed.
# Usage: ./rookie_event_study.sh [output-directory]
# Examples:
#   ./rookie_event_study.sh
#   ./rookie_event_study.sh reports/fuad/event-experiment
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"
CLASSPATH_FILE="target/classpath.txt"
if [[ ! -f "$CLASSPATH_FILE" || "pom.xml" -nt "$CLASSPATH_FILE" ]]; then
    ./mvnw -q compile dependency:build-classpath -Dmdep.outputFile="$CLASSPATH_FILE"
else
    ./mvnw -q compile
fi
java -cp "target/classes:$(cat "$CLASSPATH_FILE")" ff.projection.fuad.RookieEventStudy "$@"
