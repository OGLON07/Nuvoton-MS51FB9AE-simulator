#!/usr/bin/env bash
# Standalone (single process) vs Multi-process (UI/Core/Logger over pipes) benchmark.
# Usage: tests/run_benchmark.sh [sessions]   (default 5)  - run after ./build.sh
set -u
cd "$(dirname "$0")/.."
N=${1:-5}
OUT=benchmark; mkdir -p $OUT
rm -f $OUT/standalone.csv $OUT/multi.csv $OUT/startup_*.txt
export BENCH_OUT=$OUT/multi.csv

echo "Sessions per variant: $N   (workload: 8001-instruction program, 2000 STEPs, 2000 GET_STATEs, 20 RUNs per session)"
for i in $(seq 1 $N); do
  echo "  standalone session $i/$N"
  java -cp out BenchStandalone $OUT/standalone.csv || exit 1
  echo "  multi-process session $i/$N"
  bin/nuvoton_launcher --classpath out --ui-class BenchClient --step-delay-ms 0 --log-file /tmp/bench.log 2>/dev/null >/dev/null || exit 1
done

echo "Startup + shutdown wall time (JVM launch -> clean exit), $N reps each"
for i in $(seq 1 $N); do
  s=$(date +%s%N); java -cp out BenchNoop; e=$(date +%s%N);                       echo $(( (e-s)/1000000 )) >> $OUT/startup_standalone.txt
  s=$(date +%s%N); bin/nuvoton_launcher --classpath out --ui-class BenchQuick --log-file /tmp/bench.log >/dev/null 2>&1; e=$(date +%s%N); echo $(( (e-s)/1000000 )) >> $OUT/startup_multi.txt
done
python3 tests/summarize_benchmark.py $OUT
