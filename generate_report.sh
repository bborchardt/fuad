#!/usr/bin/env bash
# Builds the project and invokes FuadRunner, forwarding all arguments.
#
# Usage:
#   ./generate_report.sh -t <franchises|franchise_projections|rankings|rookies> [-y <year>]
#
# Examples:
#   ./generate_report.sh -t rankings
#   ./generate_report.sh -t rookies -y 2024
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
