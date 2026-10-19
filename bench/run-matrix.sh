#!/usr/bin/env bash
# Runs scenarios interleaved (S1 S2 ... S1 S2 ...) so machine drift doesn't bias one scenario.
#
#   bench/run-matrix.sh 3 S1 S2 S3 S4 S5       3 rounds of S1-S5
#   NODES=6 bench/run-matrix.sh 3 S6
#
# Builds once, then passes SKIP_BUILD=1 to every run. A failed run is logged and skipped.
set -uo pipefail

ROUNDS=${1:?usage: bench/run-matrix.sh <rounds> <scenario>...}
shift
BENCH_DIR=$(cd "$(dirname "$0")" && pwd)

(cd "$(dirname "$BENCH_DIR")" && mvn -q package -DskipTests) || exit 1
docker compose -p camunda-bench -f "$BENCH_DIR/docker-compose.yml" build -q camunda || exit 1

for round in $(seq 1 "$ROUNDS"); do
  for scenario in "$@"; do
    echo "=== round $round/$ROUNDS: $scenario (nodes=${NODES:-default}) ==="
    SKIP_BUILD=1 "$BENCH_DIR/run.sh" "$scenario" "$round" || echo "!!! FAILED: $scenario round $round"
  done
done
echo "=== matrix done ==="
