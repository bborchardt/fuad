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
# It builds clean, and that is not caution for its own sake. An incremental build leaves the class files of
# a moved or deleted class behind, and Groovy resolves them: after ReportManifest moved out of ff.run.fuad,
# a runner referencing it with no import went on compiling against the file in its old package, and the
# auction wrote seven reports and stamped none. The suite passed throughout, because tests compile against
# the same stale tree.
#
# Every other script here is incremental on purpose. Reports are regenerated constantly, sometimes between
# picks of a live draft, and twenty seconds a run is a real cost there. These figures are committed and are
# what the documentation is held to, so a figure produced by code that is no longer in the source would be
# committed as the truth about a model that cannot produce it.
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

# Clean, so nothing left over from a moved class can answer for one that is gone. The classpath file lives
# under target/ and goes with it, so it is always rebuilt here rather than only when the pom is newer.
./mvnw -q clean compile dependency:build-classpath -Dmdep.outputFile="$CLASSPATH_FILE"

java -cp "target/classes:$(cat "$CLASSPATH_FILE")" ff.run.FiguresRefresh "$@"
