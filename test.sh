#!/usr/bin/env bash
# Runs both test suites and writes the combined output to test-report.txt.
# Backend needs no API key: the Gemini client is faked in tests.
set -uo pipefail
cd "$(dirname "$0")"

REPORT=test-report.txt
: > "$REPORT"

run() {
  echo "=============================================================" | tee -a "$REPORT"
  echo "$1" | tee -a "$REPORT"
  echo "=============================================================" | tee -a "$REPORT"
  shift
  "$@" 2>&1 | tee -a "$REPORT"
  return "${PIPESTATUS[0]}"
}

run "BACKEND — JUnit 5 + Mockito (Spring Boot)" ./mvnw test
backend=$?

if [ -d frontend ]; then
  # Subshell cd rather than `npm --prefix` — see the note in start.sh.
  run "FRONTEND — Vitest + React Testing Library" bash -c 'cd frontend && npm test'
  frontend=$?
else
  echo "frontend/ not found — skipping frontend tests" | tee -a "$REPORT"
  frontend=0
fi

echo | tee -a "$REPORT"
echo "backend exit=$backend  frontend exit=$frontend" | tee -a "$REPORT"
echo "Full output written to $REPORT"
exit $(( backend | frontend ))
