#!/usr/bin/env bash
# Builds the project and invokes GreenfieldRunner, forwarding all arguments.
#
# Reports are written to <out>/<year>/<type>.tsv, defaulting to reports/greenfield/<year>.
#
# Usage:
#   ./greenfield_report.sh -t <board|keepers|picks|all> [-y <year>] [-o <dir>]
#
# Examples:
#   ./greenfield_report.sh -t all
#   ./greenfield_report.sh -t keepers -y 2026
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

CLASSPATH_FILE="target/classpath.txt"

if [[ ! -f "$CLASSPATH_FILE" || "pom.xml" -nt "$CLASSPATH_FILE" ]]; then
    ./mvnw -q compile dependency:build-classpath -Dmdep.outputFile="$CLASSPATH_FILE"
else
    ./mvnw -q compile
fi

java -cp "target/classes:$(cat "$CLASSPATH_FILE")" ff.run.greenfield.GreenfieldRunner "$@"
