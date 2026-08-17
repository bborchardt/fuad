#!/usr/bin/env bash
# Checks the model's documentation against the figures the model actually produces.
#
# Every table under a <!-- figures: ... --> marker is verified cell by cell against
# docs/figures/<year>. A figure that moves therefore fails this check in the commit that moves it,
# rather than sitting in prose looking exactly like a figure that did not move.
#
# The figures are checked first, once, against the model that is checked out: prose agreeing with
# figures a superseded model wrote is not a pass, and without this it read as one.
#
# Each document then reports how many figures it was actually held to. A document with no marker is
# reported NONE rather than OK, because nothing in it was checked and OK does not say that.
#
# Unlike check_strategy.sh there is no boundary rule here: a plan may not name a model internal, and
# documentation of the model is nothing but model internals.
#
# Usage:
#   ./check_docs.sh [<year>] [<document.md> ...]
#
# Examples:
#   ./check_docs.sh                       # 2026, every doc
#   ./check_docs.sh 2026 docs/PROJECTION.md
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

YEAR="${1:-2026}"
shift || true

if [[ $# -eq 0 ]]; then
    set -- docs/*.md
fi

CLASSPATH_FILE="target/classpath.txt"

if [[ ! -f "$CLASSPATH_FILE" || "pom.xml" -nt "$CLASSPATH_FILE" ]]; then
    ./mvnw -q compile dependency:build-classpath -Dmdep.outputFile="$CLASSPATH_FILE"
else
    ./mvnw -q compile
fi

java -cp "target/classes:$(cat "$CLASSPATH_FILE")" ff.run.DocsCheck "$YEAR" "$@"
