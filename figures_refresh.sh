#!/usr/bin/env bash
# Builds the project and writes the model's own figures to docs/figures/<year>.
#
# These are what docs/PROJECTION.md cites instead of quoting numbers into prose. They are committed, unlike
# reports/, because they describe the model rather than an auction: a change that moves a level or a depth
# should show up as a diff here in the same commit that moves it.
#
# Run this whenever the model or the data behind it changes, then ./check_docs.sh to find the prose that
# has gone stale.
#
# Usage:
#   ./figures_refresh.sh <year> [out-dir]
#
# Example:
#   ./figures_refresh.sh 2026
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

CLASSPATH_FILE="target/classpath.txt"

if [[ ! -f "$CLASSPATH_FILE" || "pom.xml" -nt "$CLASSPATH_FILE" ]]; then
    ./mvnw -q compile dependency:build-classpath -Dmdep.outputFile="$CLASSPATH_FILE"
else
    ./mvnw -q compile
fi

java -cp "target/classes:$(cat "$CLASSPATH_FILE")" ff.run.FiguresRefresh "$@"
