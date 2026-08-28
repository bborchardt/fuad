#!/usr/bin/env bash
# Checks the model's documentation against the figures the model actually produces.
#
# Every table under a <!-- figures: ... --> marker is verified cell by cell against
# docs/figures/<league>/<year>. A figure that moves therefore fails this check in the commit that moves it,
# rather than sitting in prose looking exactly like a figure that did not move.
#
# The figures are checked first, once, against the model that is checked out: prose agreeing with
# figures a superseded model wrote is not a pass, and without this it read as one.
#
# Each document then reports how many figures it was actually held to. A document with no marker is
# reported NONE rather than OK, because no figure in it was checked and OK does not say that.
#
# Links to other files in the tree are checked too, paths and headings alike. A broken markdown link fails
# silently -- nothing renders an error, and a reader concludes the documentation is confused rather than
# that a file moved underneath it. Splitting the data document by league broke two anchors that way, and
# both were found by hand. External URLs are left alone: whether a site is up is not this repository's
# question, and a check that fails when one is down is a check that gets ignored.
#
# Unlike check_strategy.sh there is no boundary rule here: a plan may not name a model internal, and
# documentation of the model is nothing but model internals.
#
# Usage:
#   ./check_docs.sh [<year>] [<document.md> ...]
#
# Examples:
#   ./check_docs.sh                       # 2026, every doc
#   ./check_docs.sh 2026 docs/fuad/PROJECTION.md
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

# The year is optional and the documents are optional, so the first argument has to say which it is.
# Taking it as the year regardless meant `./check_docs.sh docs/fuad/PROJECTION.md` — the form the README
# documents — looked for figures under docs/figures/docs/PROJECTION.md and reported a missing manifest.
YEAR=2026
if [[ ${1:-} =~ ^[0-9]{4}$ ]]; then
    YEAR="$1"
    shift
fi

# README.md is in the default set even though it carries no marked table. It makes claims about the model
# like any other document, and leaving it out of the run meant it was not reported at all — which reads the
# same as being checked and passing. Listed, it reports NONE, which is what is actually true of it.
if [[ $# -eq 0 ]]; then
    # Both leagues' documents and the shared ones, since a league whose docs nobody happened to name would
    # otherwise go unreported -- which reads the same as being checked and passing.
    set -- README.md docs/*.md docs/fuad/*.md docs/greenfield/*.md
fi

CLASSPATH_FILE="target/classpath.txt"

if [[ ! -f "$CLASSPATH_FILE" || "pom.xml" -nt "$CLASSPATH_FILE" ]]; then
    ./mvnw -q compile dependency:build-classpath -Dmdep.outputFile="$CLASSPATH_FILE"
else
    ./mvnw -q compile
fi

java -cp "target/classes:$(cat "$CLASSPATH_FILE")" ff.run.DocsCheck "$YEAR" "$@"
