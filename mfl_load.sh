#!/usr/bin/env bash
# Builds the project and invokes MflLoad, forwarding all arguments.
#
# Loads a season's auction rosters and rookie draft from the commissioner's workbook onto
# myfantasyleague.com. Both are held away from the league site, so the workbook is the only record of them
# until this runs. The full run book, including the two steps no API can perform, is in
# docs/fuad/ROSTER_LOAD.md -- read it before the first run of a season.
#
# The league id has no default. Every step names it explicitly, so no step can reach the real league
# because a variable was forgotten.
#
# Credentials come from the environment, never the command line:
#   read -rs "MFL_PASS?MFL password: "; export MFL_PASS MFL_USER=<commissioner>
#
# Usage:
#   ./mfl_load.sh -t <plan|clear|load|verify> -l <league-id> [-y <year>] [-w <workbook>] [-o <dir>]
#
# Examples:
#   ./mfl_load.sh -t plan   -l 14053          # resolve the workbook, write the lists, touch nothing
#   ./mfl_load.sh -t clear  -l 14053          # empty every roster so the lists will load
#   ./mfl_load.sh -t load   -l 14053          # contracts, rookie draft, rookie pay, injured reserve
#   ./mfl_load.sh -t verify -l 14053          # compare the league site against the plan
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

CLASSPATH_FILE="target/classpath.txt"

if [[ ! -f "$CLASSPATH_FILE" || "pom.xml" -nt "$CLASSPATH_FILE" ]]; then
    ./mvnw -q compile dependency:build-classpath -Dmdep.outputFile="$CLASSPATH_FILE"
else
    ./mvnw -q compile
fi

java -cp "target/classes:$(cat "$CLASSPATH_FILE")" ff.run.MflLoad "$@"
