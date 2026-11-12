#!/usr/bin/env bash
# Overhead benchmark for the loan observability changes: autocannon against a loan read and a repayment,
# on images built with `fineract build`. The report that explains the method and the results is
# fineract-doc/src/docs/en/chapters/architecture/observability-overhead.adoc.
set -euo pipefail

BENCH="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(git -C "$BENCH" rev-parse --show-toplevel)"
FINERACT="$ROOT/.cursor/skills/verify-fineract/scripts/fineract"
cd "$ROOT"

export FINERACT_DB=mysql
BEFORE_IMAGE="${BEFORE_IMAGE:-fineract:before}"
AFTER_IMAGE="${AFTER_IMAGE:-fineract:after}"
OUT="${BENCH_OUT:-/tmp/fineract-overhead}"
SNAPSHOT="${BENCH_SNAPSHOT:-$OUT/snapshot}"
CONNECTIONS="${CONNECTIONS:-8}"
DURATION="${DURATION:-30}"
WARMUP_READ="${WARMUP_READ:-90}"
WARMUP_REPAY="${WARMUP_REPAY:-60}"
RUNS="${RUNS:-3}"
CYCLES="${CYCLES:-6}"
FIRST_CYCLE="${FIRST_CYCLE:-1}"
POOL="${POOL:-1000}"
JVM="${JVM:-bench}"
VARIANTS=(before after after-json)
BASE_URL="https://localhost:8443/fineract-provider"

COMPOSE=(docker compose -p fineract-verify -f docker-compose-mysql-test.yml
    -f .cursor/skills/verify-fineract/compose/fineract-demo.yml -f "$BENCH/bench-compose.yml")

usage() {
    cat <<'EOF'
usage: overhead.sh <command>

  install     install autocannon (npm ci in this directory)
  seed        seed the loans on a running Before server, then stop it and snapshot the MySQL data directory
  run         CYCLES times: for each variant, restore the snapshot, start the server, warm up, measure
  start VARIANT
              restore the snapshot and start one variant (before, after, after-json), for a look by hand

environment (defaults):
  BEFORE_IMAGE=fineract:before  AFTER_IMAGE=fineract:after   images built with `fineract build`
  BENCH_OUT=/tmp/fineract-overhead   results, and the snapshot unless BENCH_SNAPSHOT names another directory
  CONNECTIONS=8  DURATION=30         autocannon connections and seconds for each measured run
  WARMUP_READ=90  WARMUP_REPAY=60    warm-up seconds after every server start
  RUNS=3  CYCLES=6                   measured runs of each workload for each start; starts of each variant
  FIRST_CYCLE=1                      number of the first cycle, to add cycles to earlier results
  POOL=1000                          loans in each pool; RUNS + 2 pools are seeded
  JVM=bench|stock                    stock: JAVA_TOOL_OPTIONS as in config/docker/env/fineract-common.env
                                     bench: the same with -Xms4G -Xmx4G instead of -Xmx1G, without
                                     -XX:TieredStopAtLevel=1 and without the JDWP agent
EOF
}

java_tool_options() {
    local stock
    stock="$(sed -n 's/^JAVA_TOOL_OPTIONS="\(.*\)"$/\1/p' config/docker/env/fineract-common.env)"
    [[ -n "$stock" ]] || { echo "JAVA_TOOL_OPTIONS not found in fineract-common.env" >&2; exit 1; }
    case "$JVM" in
        stock) echo "$stock" ;;
        bench) sed -E 's/-Xmx1G/-Xms4G -Xmx4G/; s/ -XX:TieredStopAtLevel=1//; s/ -agentlib:jdwp=[^ ]+//' <<<"$stock" ;;
        *) echo "JVM must be bench or stock" >&2; exit 1 ;;
    esac
}

wait_healthy() {
    for _ in $(seq 1 120); do
        if curl -sk "$BASE_URL/actuator/health" | grep -q '"UP"'; then
            return 0
        fi
        sleep 3
    done
    echo "fineract did not become healthy" >&2
    "${COMPOSE[@]}" logs --tail 50 fineract >&2
    exit 1
}

wait_db() {
    for _ in $(seq 1 60); do
        [[ "$(docker inspect -f '{{.State.Health.Status}}' mysql 2>/dev/null)" == healthy ]] && return 0
        sleep 2
    done
    echo "mysql did not become healthy" >&2
    exit 1
}

# pool 0: repayment warm-up, pool 1: reads, pool 1+i: measured repayment run i
pool_first() {
    echo $(($(cat "$SNAPSHOT/first-loan") + $1 * POOL))
}

snapshot_helper() {
    docker run --rm --volumes-from mysql -v "$SNAPSHOT:/snapshot" --entrypoint bash mysql:8 -c "$1"
}

seed() {
    mkdir -p "$SNAPSHOT"
    [[ "$("$FINERACT" api GET loans | python3 -c 'import json,sys; print(json.load(sys.stdin)["totalFilteredRecords"])')" == 0 ]] \
        || { echo "the server already has loans; run 'fineract down' and 'fineract up' first" >&2; exit 1; }
    "$FINERACT" seed-loans $(((RUNS + 2) * POOL)) | tee "$SNAPSHOT/seed-loans.json"
    python3 - "$SNAPSHOT" $(((RUNS + 2) * POOL)) <<'EOF'
import json, sys
seed = json.load(open(sys.argv[1] + "/seed-loans.json"))
expected = int(sys.argv[2])
assert seed["failures"] == 0 and seed["loans"] == expected, seed
assert seed["maxLoanId"] - seed["minLoanId"] + 1 == expected, "loan ids are not contiguous"
open(sys.argv[1] + "/first-loan", "w").write(str(seed["minLoanId"]))
EOF
    "$FINERACT" doctor | tee "$SNAPSHOT/seeded-by.txt"
    "${COMPOSE[@]}" stop fineract db
    snapshot_helper 'tar -C /var/lib/mysql -cf /snapshot/mysql-data.tar . && sha256sum /snapshot/mysql-data.tar > /snapshot/mysql-data.tar.sha256'
    echo "snapshot: $SNAPSHOT/mysql-data.tar ($(du -h "$SNAPSHOT/mysql-data.tar" | cut -f1))"
}

start_variant() {
    local variant="$1" image json
    case "$variant" in
        before) image="$BEFORE_IMAGE" json=false ;;
        after) image="$AFTER_IMAGE" json=false ;;
        after-json) image="$AFTER_IMAGE" json=true ;;
        *) echo "unknown variant $variant" >&2; exit 1 ;;
    esac
    "${COMPOSE[@]}" stop fineract db >/dev/null 2>&1
    snapshot_helper 'cd /snapshot && sha256sum -c --quiet mysql-data.tar.sha256 && find /var/lib/mysql -mindepth 1 -delete && tar -C /var/lib/mysql -xpf mysql-data.tar'
    "${COMPOSE[@]}" start db >/dev/null 2>&1
    wait_db
    export FINERACT_IMAGE="$image" FINERACT_LOGGING_JSON_ENABLED="$json"
    "${COMPOSE[@]}" up -d --no-deps --force-recreate fineract >/dev/null 2>&1
    wait_healthy
}

cpu_usec() {
    sed -n 's/^usage_usec //p' "/sys/fs/cgroup/docker/$(docker inspect -f '{{.Id}}' "$1")/cpu.stat"
}

# one autocannon run; appends one line to runs.jsonl and stops the benchmark when a request failed
autocannon_run() {
    local phase="$1" workload="$2" pool="$3" seconds="$4" index="$5"
    local server_cpu db_cpu result status=0
    server_cpu="$(cpu_usec fineract-verify-fineract-1)"
    db_cpu="$(cpu_usec mysql)"
    result="$(node "$BENCH/autocannon-run.js" --workload "$workload" --first-loan "$(pool_first "$pool")" --loans "$POOL" \
        --connections "$CONNECTIONS" --duration "$seconds")" || status=$?
    server_cpu=$(($(cpu_usec fineract-verify-fineract-1) - server_cpu))
    db_cpu=$(($(cpu_usec mysql) - db_cpu))
    RESULT="$result" python3 - "$JVM" "$CYCLE" "$POSITION" "$VARIANT" "$phase" "$index" "$server_cpu" "$db_cpu" >>"$OUT/runs.jsonl" <<'EOF'
import json, os, subprocess, sys
jvm, cycle, position, variant, phase, index, server_cpu, db_cpu = sys.argv[1:]
run = json.loads(os.environ["RESULT"])
logs = subprocess.run(["docker", "logs", "--since", run["start"], "--until", run["finish"], "fineract-verify-fineract-1"],
                      capture_output=True)
print(json.dumps({"jvm": jvm, "cycle": int(cycle), "position": int(position), "variant": variant, "phase": phase,
                  "run": int(index), "serverCpuUsec": int(server_cpu), "dbCpuUsec": int(db_cpu),
                  "serverLogLines": (logs.stdout + logs.stderr).count(b"\n"), **run}))
EOF
    tail -n 1 "$OUT/runs.jsonl" | python3 -c '
import json, sys
r = json.load(sys.stdin)
l = r["latencyMs"]
print("  {phase:7} {workload:5} run {run}: {requestsPerSecond:8.1f} req/s  p50 {p50:7.2f}  p97.5 {p975:7.2f}  p99 {p99:7.2f} ms  2xx {responses2xx}  non-2xx {non2xx}  log lines {serverLogLines}".format(p50=l["p50"], p975=l["p97_5"], p99=l["p99"], **r))'
    if [[ "$status" != 0 ]]; then
        echo "autocannon run failed: $result" >&2
        exit 1
    fi
}

measure_variant() {
    local started
    started="$(date +%s)"
    start_variant "$VARIANT"
    {
        echo "cycle $CYCLE position $POSITION variant $VARIANT jvm $JVM"
        "$FINERACT" doctor
        docker inspect -f 'image id: {{.Image}}' fineract-verify-fineract-1
        docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' fineract-verify-fineract-1 \
            | grep -E '^(JAVA_TOOL_OPTIONS|FINERACT_LOGGING_JSON_ENABLED|FINERACT_SERVER_TOMCAT_MAX_KEEP_ALIVE_REQUESTS|FINERACT_MANAGEMENT_METRICS_DISTRIBUTION_HTTP_SERVER_REQUESTS)='
    } >"$OUT/server-$JVM-$CYCLE-$VARIANT.txt"
    echo "cycle $CYCLE, $VARIANT ($(sed -n 's/^server: *//p' "$OUT/server-$JVM-$CYCLE-$VARIANT.txt")), up after $(($(date +%s) - started)) s"

    autocannon_run warmup read 1 "$WARMUP_READ" 0
    autocannon_run warmup repay 0 "$WARMUP_REPAY" 0
    for run in $(seq 1 "$RUNS"); do
        autocannon_run measure read 1 "$DURATION" "$run"
        autocannon_run measure repay $((run + 1)) "$DURATION" "$run"
    done

    curl -sk -o "$OUT/prometheus-$JVM-$CYCLE-$VARIANT.txt" -w 'scrape: %{size_download} bytes in %{time_total} s\n' \
        "$BASE_URL/actuator/prometheus" >>"$OUT/server-$JVM-$CYCLE-$VARIANT.txt"
}

BENCH_JAVA_TOOL_OPTIONS="$(java_tool_options)"
export BENCH_JAVA_TOOL_OPTIONS

case "${1:-}" in
    install)
        (cd "$BENCH" && npm ci --no-audit --no-fund)
        ;;
    seed)
        seed
        ;;
    start)
        start_variant "${2:?variant}"
        "$FINERACT" doctor
        ;;
    run)
        [[ -f "$SNAPSHOT/mysql-data.tar" ]] || { echo "no snapshot; run 'overhead.sh seed' first" >&2; exit 1; }
        {
            date -u +'started %Y-%m-%dT%H:%M:%SZ'
            echo "jvm=$JVM connections=$CONNECTIONS duration=$DURATION warmup_read=$WARMUP_READ warmup_repay=$WARMUP_REPAY runs=$RUNS first_cycle=$FIRST_CYCLE cycles=$CYCLES pool=$POOL"
            echo "JAVA_TOOL_OPTIONS=$BENCH_JAVA_TOOL_OPTIONS"
        } | tee -a "$OUT/settings-$JVM.txt"
        for CYCLE in $(seq "$FIRST_CYCLE" $((FIRST_CYCLE + CYCLES - 1))); do
            # rotate the order so that no variant always runs first
            for POSITION in 0 1 2; do
                VARIANT="${VARIANTS[$(((CYCLE - 1 + POSITION) % 3))]}"
                measure_variant
            done
        done
        date -u +'finished %Y-%m-%dT%H:%M:%SZ' | tee -a "$OUT/settings-$JVM.txt"
        ;;
    *)
        usage
        exit 1
        ;;
esac
