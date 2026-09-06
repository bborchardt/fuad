#!/usr/bin/env bash
# Run rookie research from any directory; production commands remain at the repository root.
# Usage: ./research/run.sh <study> [arguments...]
# Examples:
#   ./research/run.sh chronological-study
#   ./research/run.sh snapshot-verify research/snapshots/2026-09-06-qb-events
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO"
command="${1:-help}"
if [[ "$command" == help || "$command" == --help ]]; then
    echo 'Studies: backtest, qb-diagnostics, availability-study, rate-study, event-study, event-calibration,'
    echo '         rank-frequency, chronology-audit, chronological-study, prospective-capture, capture-audit'
    echo 'Git records: snapshot-export, snapshot-verify, snapshot-capture'
    exit 0
fi
shift
case "$command" in
    backtest) main=RookieBacktestRunner ;;
    qb-diagnostics) main=RookieQbDiagnostics ;;
    availability-study) main=RookieAvailabilityStudy ;;
    rate-study) main=RookieRateStudy ;;
    event-study) main=RookieEventStudy ;;
    event-calibration) main=RookieEventCalibration ;;
    rank-frequency) main=RookieRankFrequency ;;
    chronology-audit) main=RookieChronologyAudit ;;
    chronological-study) main=RookieChronologicalStudy ;;
    prospective-capture) main=RookieProspectiveCapture ;;
    capture-audit) main=RookieCaptureAudit ;;
    snapshot-export|snapshot-verify|snapshot-capture) main=ResearchSnapshot; set -- "${command#snapshot-}" "$@" ;;
    *) echo "Unknown study: $command" >&2; exit 2 ;;
esac
./mvnw -q -f research/pom.xml compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "research/target/classes:$(cat research/target/classpath.txt)" "ff.research.fuad.rookies.$main" "$@"
