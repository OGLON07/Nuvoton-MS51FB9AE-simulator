#!/usr/bin/env bash
# Week-4 IPC system tests. Runs the REAL three-process system (C launcher using
# pipe/fork/dup2/exec) with a scripted client in place of the Swing UI.
# Usage: tests/run_ipc_tests.sh        (from the project root, after ./build.sh)
set -u
cd "$(dirname "$0")/.."
LAUNCHER=bin/nuvoton_launcher
CP=out
TMP=$(mktemp -d)
PASS=0; FAIL=0
ok()   { echo "  PASS  $1"; PASS=$((PASS+1)); }
bad()  { echo "  FAIL  $1  -> $2"; FAIL=$((FAIL+1)); }
chk()  { if eval "$2"; then ok "$1"; else bad "$1" "$3"; fi; }

[ -x $LAUNCHER ] && [ -d $CP/Main ] || { echo "Run ./build.sh first"; exit 2; }

echo "=== Run 1: full scripted session (TC01-TC07, TC10a, TC11) ==="
LOG=$TMP/run1.log
timeout 120 $LAUNCHER --classpath $CP --ui-class UiScriptClient --step-delay-ms 0 --log-file $LOG \
    > $TMP/run1.out 2> $TMP/run1.err
RC=$?
grep -E "^  (PASS|FAIL)|^      " $TMP/run1.err
grep -q "^  FAIL" $TMP/run1.err && FAIL=$((FAIL+$(grep -c "^  FAIL" $TMP/run1.err)))
PASS=$((PASS+$(grep -c "^  PASS" $TMP/run1.err)))

echo
echo "=== Logger checks (TC08 execution log, TC09 error log) ==="
chk "TC08  'Program loaded' in log file"   "grep -q '\[EXECUTION\] Program loaded' $LOG"  "missing"
chk "TC08  'STEP executed' + instruction"  "grep -q 'STEP executed | PC=0x0000 MOV_A_IMM' $LOG" "missing"
chk "TC08  'RUN started' in log file"      "grep -q '\[EXECUTION\] RUN started' $LOG"    "missing"
chk "TC08  'RUN stopped: HALTED' pushed by worker" "grep -q 'RUN stopped: HALTED' $LOG"  "missing"
chk "TC08  PAUSE and RESET logged"         "grep -q '\[EXECUTION\] PAUSE' $LOG && grep -q '\[EXECUTION\] RESET' $LOG" "missing"
chk "TC09  Core error (STEP on halted CPU) logged as ERROR" "grep -q '\[ERROR\] STEP failed | CPU is halted' $LOG" "missing"
chk "TC09  invalid command logged as ERROR" "grep -q '\[ERROR\] Invalid command received' $LOG" "missing"
chk "TC09  no FETCH/DECODE noise in log"   "! grep -q 'FETCH:\|DECODE:' $LOG"  "CPU debug prints reached the Logger"

echo
echo "=== TC10: clean shutdown, no orphans ==="
chk "TC10  launcher exit code 0"            "[ $RC -eq 0 ]" "rc=$RC"
chk "TC10  launcher reports clean shutdown" "grep -q 'clean shutdown' $TMP/run1.err" "$(tail -2 $TMP/run1.err | tr '\n' ' ')"
chk "TC10  all 3 children exited with status 0" "[ \$(grep -c 'exited (status 0)' $TMP/run1.err) -eq 3 ]" "$(grep exited $TMP/run1.err | tr '\n' ' ')"
chk "TC10  Core logged SHUTDOWN + shutting down" "grep -q 'SHUTDOWN received' $LOG && grep -q 'Core process shutting down' $LOG" "missing"
chk "TC10  Logger stopped cleanly"          "grep -q 'Logger process stopped' $TMP/run1.out" "missing"
PIDS=$(grep -oE 'pid=[0-9]+' $TMP/run1.err | head -4 | cut -d= -f2 | tr '\n' ' ')
ALIVE=0; for p in $PIDS; do kill -0 $p 2>/dev/null && ALIVE=$((ALIVE+1)); done
chk "TC10  no launcher/child pid still alive ($PIDS)" "[ $ALIVE -eq 0 ]" "$ALIVE alive"
chk "TC10  no stray simulator java processes" "! pgrep -f 'Main.core.CoreProcess|Main.Logger.LoggerProcess|UiScriptClient' >/dev/null" "$(pgrep -af 'Main.core.CoreProcess|Main.Logger.LoggerProcess' | head -2)"

echo
echo "=== Run 2: UI crashes (SIGKILL) -> Core and Logger must exit via pipe EOF ==="
LOG2=$TMP/run2.log
$LAUNCHER --classpath $CP --ui-class UiIdleClient --log-file $LOG2 > $TMP/run2.out 2> $TMP/run2.err &
LPID=$!
for i in $(seq 1 100); do grep -q 'UiIdleClient\] alive' $TMP/run2.err 2>/dev/null && break; sleep 0.1; done
UIPID=$(grep -oE 'UI pid=[0-9]+' $TMP/run2.err | head -1 | cut -d= -f2)
COREPID=$(grep -oE 'CORE pid=[0-9]+' $TMP/run2.err | head -1 | cut -d= -f2)
LOGPID=$(grep -oE 'LOGGER pid=[0-9]+' $TMP/run2.err | head -1 | cut -d= -f2)
sleep 1   # let Core/Logger finish JVM startup
kill -9 $UIPID
T0=$(date +%s.%N)
wait $LPID; RC2=$?
T1=$(date +%s.%N)
SECS=$(echo "$T1 - $T0" | bc)
chk "TC10b UI killed -> launcher ends by itself in ${SECS}s (<5s grace)" "[ \$(echo \"$SECS < 5\" | bc) -eq 1 ]" "took ${SECS}s"
chk "TC10b Core exited (stdin EOF)"   "! kill -0 $COREPID 2>/dev/null"  "core alive"
chk "TC10b Logger exited (log EOF)"   "! kill -0 $LOGPID 2>/dev/null"   "logger alive"
chk "TC10b no 'SIGTERM' needed (EOF cascade only)" "! grep -q 'SIGTERM' $TMP/run2.err" "$(grep SIGTERM $TMP/run2.err)"
chk "TC10b Core logged its shutdown after UI vanished" "grep -q 'Core process shutting down' $LOG2" "missing"

echo
echo "=== TC12: process-separation rules (static check of the source) ==="
chk "UI does not touch CPU/Memory/Queue classes" "! grep -rnE 'new CPU\(|import Main\.(CPU|Memory|Queue|instruction)' src/Main/UI" "$(grep -rnE 'new CPU\(|import Main\.(CPU|Memory|Queue|instruction)' src/Main/UI | head -3)"
chk "Core has no Swing/AWT"                      "! grep -rnE 'javax\.swing|java\.awt' src/Main/core" "found"
chk "Logger has no Swing/AWT/CPU dependency"     "! grep -rnE 'javax\.swing|java\.awt|Main\.(CPU|Memory|core)' src/Main/Logger" "found"

echo
echo "TOTAL: $PASS passed, $FAIL failed   (logs kept in $TMP)"
[ $FAIL -eq 0 ]
