#!/usr/bin/env bash
# Nested QB outcome-pooling experiment; production valuations are unchanged.
# Usage: ./rookie_availability_study.sh [output-directory]
# Examples:
#   ./rookie_availability_study.sh
#   ./rookie_availability_study.sh reports/fuad/availability-experiment
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"
CLASSPATH_FILE="target/classpath.txt"
if [[ ! -f "$CLASSPATH_FILE" || "pom.xml" -nt "$CLASSPATH_FILE" ]]; then
    ./mvnw -q compile dependency:build-classpath -Dmdep.outputFile="$CLASSPATH_FILE"
else
    ./mvnw -q compile
fi
java -cp "target/classes:$(cat "$CLASSPATH_FILE")" ff.projection.fuad.RookieAvailabilityStudy "$@"
