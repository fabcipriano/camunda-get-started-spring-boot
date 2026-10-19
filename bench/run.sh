#!/usr/bin/env bash
# Runs one job acquisition benchmark scenario (see PLAN.md).
#
#   bench/run.sh <scenario> [run-number]      e.g. bench/run.sh S2 1
#   NODES=6 bench/run.sh S6 1                  override the node count
#
# Other env knobs: COUNT (instances, default 50000), SKIP_BUILD=1, KEEP_UP=0 (tear down after),
# DB_CPUS (limit Postgres CPUs), JOB_EXECUTOR_CORE_POOL_SIZE / _MAX_POOL_SIZE, MAX_WAIT_S.
#
# Output: bench/results/<scenario>-n<nodes>-r<run>-<timestamp>/ and one line in bench/results/summary.csv
set -euo pipefail

SCENARIO=${1:?usage: bench/run.sh <scenario> [run-number]}
RUN=${2:-1}
BENCH_DIR=$(cd "$(dirname "$0")" && pwd)
ROOT_DIR=$(dirname "$BENCH_DIR")
SCENARIO_FILE="$BENCH_DIR/scenarios/$SCENARIO.env"
[ -f "$SCENARIO_FILE" ] || { echo "No scenario file $SCENARIO_FILE" >&2; exit 1; }

set -a
# shellcheck source=/dev/null
source "$SCENARIO_FILE"
COUNT=${COUNT:-50000}
set +a
MAX_WAIT_S=${MAX_WAIT_S:-1800}
KEEP_UP=${KEEP_UP:-1}

DC=(docker compose -p camunda-bench -f "$BENCH_DIR/docker-compose.yml")
OUT="$BENCH_DIR/results/$SCENARIO-n$NODES-r$RUN-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"

log() { echo "[$(date +%H:%M:%S)] $*"; }
now_ms() { date +%s%3N; }
host_port() { "${DC[@]}" port --index "$1" camunda "$2" | sed 's/.*://'; }

STATS_PID=""
cleanup() {
  [ -n "$STATS_PID" ] && kill "$STATS_PID" 2>/dev/null || true
  if [ "$KEEP_UP" = 0 ]; then "${DC[@]}" down -v >/dev/null 2>&1 || true; fi
}
trap cleanup EXIT

wait_node_ready() {
  local port; port=$(host_port "$1" 8080)
  for _ in $(seq 1 180); do
    curl -sf "http://localhost:$port/benchmark/status" >/dev/null 2>&1 && return 0
    sleep 1
  done
  echo "Node $1 (port $port) not ready after 180s" >&2
  "${DC[@]}" logs --tail 50 camunda >&2
  exit 1
}

log "Scenario $SCENARIO run $RUN: nodes=$NODES count=$COUNT maxJobs=$JOB_EXECUTOR_MAX_JOBS_PER_ACQUISITION" \
    "backoff=$JOB_EXECUTOR_BACKOFF_TIME_IN_MILLIS/$JOB_EXECUTOR_MAX_BACKOFF/$JOB_EXECUTOR_WAIT_INCREASE_FACTOR"
env | grep -E '^(NODES|COUNT|DB_CPUS|JOB_EXECUTOR_|BENCHMARK_|DB_POOL_)' | sort > "$OUT/config.env"

if [ "${SKIP_BUILD:-0}" != 1 ]; then
  log "Building jar and image"
  (cd "$ROOT_DIR" && mvn -q package -DskipTests)
  "${DC[@]}" build -q camunda
fi

log "Resetting stack (empty database)"
"${DC[@]}" down -v --remove-orphans >/dev/null 2>&1
"${DC[@]}" up -d postgres-db prometheus grafana >/dev/null 2>&1
for _ in $(seq 1 60); do
  "${DC[@]}" exec -T postgres-db pg_isready -U camunda >/dev/null 2>&1 && break
  sleep 1
done

# One node first so only it creates the Camunda schema, then scale out
log "Starting node 1 (creates schema)"
"${DC[@]}" up -d --no-deps --scale camunda=1 camunda >/dev/null 2>&1
wait_node_ready 1
if [ "$NODES" -gt 1 ]; then
  log "Scaling to $NODES nodes"
  "${DC[@]}" up -d --no-deps --scale camunda="$NODES" camunda >/dev/null 2>&1
  for i in $(seq 2 "$NODES"); do wait_node_ready "$i"; done
fi
PORTS=(); MGMT_PORTS=()
for i in $(seq 1 "$NODES"); do PORTS+=("$(host_port "$i" 8080)"); MGMT_PORTS+=("$(host_port "$i" 8383)"); done
log "Nodes ready on ports ${PORTS[*]}"

log "Filling $COUNT instances"
curl -sf -XPOST --max-time 3600 "http://localhost:${PORTS[0]}/benchmark/fill?count=$COUNT" > "$OUT/fill.json"
REMAINING=$(curl -sf "http://localhost:${PORTS[0]}/benchmark/status" | python3 -c 'import sys,json;print(json.load(sys.stdin)["remainingJobs"])')
[ "$REMAINING" = "$COUNT" ] || { echo "Expected $COUNT jobs after fill, found $REMAINING" >&2; exit 1; }

# Container CPU/mem every ~1s (docker stats CPU% is per core: 200% = 2 cores busy)
echo "ts_ms,container,cpu_pct,mem" > "$OUT/docker_stats.csv"
(
  while true; do
    ts=$(now_ms)
    docker stats --no-stream --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}}' $("${DC[@]}" ps -q) \
      | sed "s/^/$ts,/; s/%//" >> "$OUT/docker_stats.csv"
  done
) &
STATS_PID=$!

log "Starting job executors on all nodes"
T0=$(now_ms)
START_PIDS=()
for port in "${PORTS[@]}"; do curl -sf -XPOST "http://localhost:$port/benchmark/start" >/dev/null & START_PIDS+=($!); done
wait "${START_PIDS[@]}"

echo "ts_ms,elapsed_ms,remaining_jobs" > "$OUT/progress.csv"
while true; do
  ts=$(now_ms)
  REMAINING=$(curl -sf "http://localhost:${PORTS[0]}/benchmark/status" | python3 -c 'import sys,json;print(json.load(sys.stdin)["remainingJobs"])')
  echo "$ts,$((ts - T0)),$REMAINING" >> "$OUT/progress.csv"
  [ "$REMAINING" = 0 ] && break
  if [ $(( (ts - T0) / 1000 )) -ge "$MAX_WAIT_S" ]; then echo "Timeout: $REMAINING jobs left" >&2; exit 1; fi
  sleep 1
done
T1=$(now_ms)
kill "$STATS_PID" 2>/dev/null || true; STATS_PID=""
log "Drained $COUNT jobs in $((T1 - T0)) ms"

sleep 3  # let the 2s metric gauges catch up
echo "node,port,acquisition_attempts,acquired_ok,acquired_failed,rejected,successful" > "$OUT/nodes.csv"
for i in $(seq 0 $((NODES - 1))); do
  curl -sf "http://localhost:${MGMT_PORTS[$i]}/actuator/prometheus" | awk -v n=$((i + 1)) -v p="${PORTS[$i]}" '
    /^camunda_count_job_acquisition_attempt / {a=$2} /^camunda_count_job_acquired_successful / {s=$2}
    /^camunda_count_job_acquired_failed / {f=$2} /^camunda_count_job_execution_rejected / {r=$2}
    /^camunda_count_job_successful / {ok=$2}
    END {printf "%s,%s,%d,%d,%d,%d,%d\n", n, p, a, s, f, r, ok}' >> "$OUT/nodes.csv"
done
OLE=$("${DC[@]}" logs camunda 2>&1 | grep -c OptimisticLockingException || true)

python3 - "$OUT" "$SCENARIO" "$RUN" "$T0" "$T1" "$OLE" "$BENCH_DIR/results/summary.csv" <<'EOF'
import csv, json, os, sys
out, scenario, run, t0, t1, ole, summary_path = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]), int(sys.argv[5]), int(sys.argv[6]), sys.argv[7]
cfg = dict(l.strip().split("=", 1) for l in open(f"{out}/config.env") if "=" in l)
nodes = list(csv.DictReader(open(f"{out}/nodes.csv")))
tot = lambda k: sum(int(n[k]) for n in nodes)
db = [float(r["cpu_pct"]) for r in csv.DictReader(open(f"{out}/docker_stats.csv"))
      if "postgres" in r["container"] and t0 <= int(r["ts_ms"]) <= t1]
count = int(cfg["COUNT"]); drain_ms = t1 - t0
row = {
    "scenario": scenario, "run": run, "nodes": cfg["NODES"], "count": count,
    "max_jobs": cfg["JOB_EXECUTOR_MAX_JOBS_PER_ACQUISITION"],
    "backoff": f'{cfg["JOB_EXECUTOR_BACKOFF_TIME_IN_MILLIS"]}/{cfg["JOB_EXECUTOR_MAX_BACKOFF"]}/{cfg["JOB_EXECUTOR_WAIT_INCREASE_FACTOR"]}',
    "threads": cfg.get("JOB_EXECUTOR_CORE_POOL_SIZE", "90"), "db_cpus": cfg.get("DB_CPUS", "0"),
    "fill_ms": json.load(open(f"{out}/fill.json"))["durationMs"],
    "drain_ms": drain_ms, "jobs_per_s": round(count * 1000 / drain_ms, 1),
    "acquisition_attempts": tot("acquisition_attempts"), "acquired_ok": tot("acquired_ok"),
    "acquired_failed": tot("acquired_failed"), "rejected": tot("rejected"), "successful": tot("successful"),
    "failed_ratio": round(tot("acquired_failed") / max(1, tot("acquired_ok") + tot("acquired_failed")), 4),
    "db_cpu_avg_pct": round(sum(db) / len(db), 1) if db else "", "db_cpu_max_pct": max(db) if db else "",
    "optimistic_locking_exceptions": ole, "results_dir": os.path.basename(out),
}
json.dump(row, open(f"{out}/summary.json", "w"), indent=2)
new = not os.path.exists(summary_path)
with open(summary_path, "a", newline="") as f:
    w = csv.DictWriter(f, fieldnames=list(row))
    if new: w.writeheader()
    w.writerow(row)
print(json.dumps(row, indent=2))
EOF

if [ "$KEEP_UP" = 1 ]; then
  log "Stack left up. Grafana: http://localhost:13000/d/camunda-bench  Prometheus: http://localhost:19090"
  log "Tear down: docker compose -p camunda-bench -f bench/docker-compose.yml down -v"
fi
