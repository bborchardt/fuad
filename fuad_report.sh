#!/usr/bin/env bash
# Builds the project and invokes FuadRunner, forwarding all arguments.
#
# Reports are written to <out>/<year>/<type>.tsv (schedule.csv), defaulting to reports/fuad/<year>.
#
# Usage:
#   ./fuad_report.sh -t <franchises|franchise_projections|rankings|rookies|salaries|teams|schedule|all> [-y <year>] [-o <dir>]
#   ./fuad_report.sh -t roster -f <franchise-id> [-y <year>] [-o <dir>]
#
# `roster` reports for one team and is not part of `all`. It writes three files, the per-player fit, the
# depth curve and the cost ladder, and takes about a minute: it replays the season several hundred times.
#
# Examples:
#   ./fuad_report.sh -t rankings
#   ./fuad_report.sh -t rookies -y 2024
#   ./fuad_report.sh -t salaries -y 2026
#   ./fuad_report.sh -t roster -f 0001 -y 2026
#   ./fuad_report.sh -t all
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

CLASSPATH_FILE="target/classpath.txt"

# Compile sources and (re)generate the dependency classpath file, but only
# regenerate the classpath when it's missing or the pom has changed since.
if [[ ! -f "$CLASSPATH_FILE" || "pom.xml" -nt "$CLASSPATH_FILE" ]]; then
    ./mvnw -q compile dependency:build-classpath -Dmdep.outputFile="$CLASSPATH_FILE"
else
    ./mvnw -q compile
fi

java -cp "target/classes:$(cat "$CLASSPATH_FILE")" ff.run.fuad.FuadRunner "$@"
