#!/usr/bin/env bash
# Builds the project and invokes DataRefresh, forwarding all arguments.
# Fetches the latest MFL data for the given year into src/main/resources/ff/mfl/data/<year>.
#
# Rankings are NOT fetched. Download them from fantasypros by hand into
# src/main/resources/ff/fantasypros/data/<year>, and include kickers: the export defaults to
# offensive players only, and a set missing a position fails RankingCoverageSpec. See docs/DATA.md.
#
# Usage:
#   ./data_refresh.sh <year>
#
# Example:
#   ./data_refresh.sh 2025
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

java -cp "target/classes:$(cat "$CLASSPATH_FILE")" ff.run.DataRefresh "$@"
