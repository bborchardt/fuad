#!/usr/bin/env bash
# Builds the project and invokes SeasonHistoryRefresh, forwarding all arguments.
# Fetches, for each given completed season, the record of that season into
# src/main/resources/ff/mfl/data/<year>:
#   rosters_post_draft.json   week 1, holding what players were signed for in that year's auction
#   rosters_end_of_year.json  the season's final rosters, which the next year's pre draft rosters come from
#   transactions.json         every move made that season, including the commissioner's expansion drafts
# and into src/main/resources/ff/nflverse/data/<year>:
#   player_stats.tsv          the raw weekly statistics every expected point is built from
#
# Unlike data_refresh.sh this writes none of the files that cannot be refetched, so it is safe to run for
# past years: it will not overwrite the pre draft snapshot in rosters.json.
#
# A year's rosters are skipped if its auction has not been entered yet, since its contracts are still
# wiped to 0.01.
#
# Usage:
#   ./season_history_refresh.sh <year> [<year> ...]
#
# Example:
#   ./season_history_refresh.sh 2017 2018 2019
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

java -cp "target/classes:$(cat "$CLASSPATH_FILE")" ff.run.SeasonHistoryRefresh "$@"
