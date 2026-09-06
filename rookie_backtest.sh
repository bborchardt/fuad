#!/usr/bin/env bash
# Leave one rookie class out of every fitted input; writes diagnostics under reports/.
# Includes nested class holdouts for calibration and shrinkage selection.
# A full run was observed to take about seven minutes; runtime depends on the machine and inputs.
#
# Usage:
#   ./rookie_backtest.sh [output-directory]
#
# Examples:
#   ./rookie_backtest.sh                         # reports/fuad/backtest
#   ./rookie_backtest.sh reports/fuad/experiment # alternate output directory
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"
CLASSPATH_FILE="target/classpath.txt"
if [[ ! -f "$CLASSPATH_FILE" || "pom.xml" -nt "$CLASSPATH_FILE" ]]; then
    ./mvnw -q compile dependency:build-classpath -Dmdep.outputFile="$CLASSPATH_FILE"
else
    ./mvnw -q compile
fi
java -cp "target/classes:$(cat "$CLASSPATH_FILE")" ff.run.fuad.RookieBacktestRunner "$@"
