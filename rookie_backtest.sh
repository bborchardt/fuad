#!/usr/bin/env bash
# Leave one rookie class out of every fitted input; writes diagnostics under reports/.
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
